package lsi.ubu.servicios;

import java.math.BigDecimal;
import java.sql.Connection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lsi.ubu.excepciones.AlquilerCochesException;
import lsi.ubu.util.PoolDeConexiones;
import lsi.ubu.util.exceptions.SGBDError;
import lsi.ubu.util.exceptions.oracle.OracleSGBDErrorUtil;

public class ServicioImpl implements Servicio {
	private static final Logger LOGGER = LoggerFactory.getLogger(ServicioImpl.class);

	private static final int DIAS_DE_ALQUILER = 4;

	public void alquilar(String nifCliente, String matricula, Date fechaIni, Date fechaFin) throws SQLException {
		PoolDeConexiones pool = PoolDeConexiones.getInstance();

		Connection con = null;
		PreparedStatement st = null;
		ResultSet rs = null;
		
		//Para la comporbacion de cliente y vehiculo
		PreparedStatement inexCliente = null; //FAlta de cerrar abajo
		PreparedStatement inexMatricula = null; //Falta de cerrar abajo
		PreparedStatement dispVehiculo = null; //Falta de cerrar abajo
		
		ResultSet rsCliente = null;
		ResultSet rsMatricula = null;
		ResultSet rsVehiculo = null;

		// Cambio de fecha a sql
		java.sql.Date sqlFechaIni = new java.sql.Date(fechaIni.getTime());
		
		java.sql.Date sqlFechaFin = null;
		
		if (fechaFin!=null) {
			sqlFechaFin = new java.sql.Date(fechaFin.getTime());
		}

				
		
		/*
		 * El calculo de los dias se da hecho
		 */
		long diasDiff = DIAS_DE_ALQUILER;
		if (fechaFin != null) {
			diasDiff = TimeUnit.MILLISECONDS.toDays(fechaFin.getTime() - fechaIni.getTime());

			if (diasDiff < 1) {
				throw new AlquilerCochesException(AlquilerCochesException.SIN_DIAS);
			}
		}
		

		try {
			con = pool.getConnection();
			
			//Comprobaciones previas vehiculo
			try {
				//Obtener vehiculo con la matricula indicada
				inexMatricula= con.prepareStatement("SELECT * FROM vehiculos WHERE matricula = ?");
				inexMatricula.setString(1, matricula);
				
				rsMatricula = inexMatricula.executeQuery();
				
				//Si no existe, lanzar excepcion
				if(!rsMatricula.next()) {
					throw new AlquilerCochesException(AlquilerCochesException.VEHICULO_NO_EXIST);
				}	
			} finally {
				if (inexMatricula !=null) {
					inexMatricula.close();
				}
				if(rsMatricula != null) {
					rsMatricula.close();
					
				}
			}
			
			//Comprobaciones previas cliente
			try {
				//Obtener cliente con el NIF indicado
				inexCliente = con.prepareStatement("SELECT * FROM clientes WHERE NIF = ?");
				inexCliente.setString(1, nifCliente);
				
				rsCliente = inexCliente.executeQuery();
				
				//Si no existe, lanzar excepcion
				if(!rsCliente.next()) {
					throw new AlquilerCochesException(AlquilerCochesException.CLIENTE_NO_EXIST);
				}	
			} finally {
				if(inexCliente != null) {
					inexCliente.close();
				}
				if(rsCliente != null) {
					rsCliente.close();
				}
			}
			
			
			//Comprobaciones previas coche no disponible
			try {
				//Obtenemos las fechas de inicio y fin de reservas apuntadas del coche indicado
				dispVehiculo= con.prepareStatement("SELECT fecha_ini, fecha_fin FROM reservas WHERE matricula = ?");
				dispVehiculo.setString(1, matricula);
				
				rsVehiculo = dispVehiculo.executeQuery();

				//De cada reserva de dicho vehiculo...
				while (rsVehiculo.next()) {
					
					java.sql.Date ultimaResFechaFin=rsVehiculo.getDate("fecha_fin");
					java.sql.Date ultimaResFechaIni=rsVehiculo.getDate("fecha_ini");
					
					//Si la fecha de inicio de la reserva a realizar no es posterior al fin de otra
					// y la fecha de fin no es anterior al inicio,
					// se lanza la excepcion
					if (!(sqlFechaIni.after(ultimaResFechaFin)  || sqlFechaFin.before(ultimaResFechaIni))) {
						throw new AlquilerCochesException(AlquilerCochesException.VEHICULO_OCUPADO);
					}
				}
				
			} finally {
				if (dispVehiculo!=null) {
					dispVehiculo.close();
				}
				if(rsVehiculo != null) {
					rsVehiculo.close();
				}
			}
			
			//Tras las comprobaciones, insertamos la nueva reserva
			st = con.prepareStatement("INSERT INTO reservas (idReserva, Cliente, matricula, fecha_ini, fecha_fin) "
					+ "VALUES(seq_reservas.nextval, ?, ?, ?, ?)");
			st.setString(1, nifCliente);
			st.setString(2, matricula);
			st.setDate(3, sqlFechaIni);
			st.setDate(4, sqlFechaFin);

			st.executeUpdate();
			
			//Obtenemos datos necesarios para calcular la factura
			String query = "SELECT precio_cada_dia, capacidad_deposito, precio_por_litro, id_modelo, tipo_combustible "
					+ "FROM vehiculos "
					+ "NATURAL JOIN modelos "
					+ "NATURAL JOIN precio_combustible "
					+ "WHERE matricula = ?";
			
			st = con.prepareStatement(query);
			st.setString(1, matricula);
			
			rs = st.executeQuery();
			
			rs.next();
			
			//Calculamos el precio del alquiler multiplicando el precio por dia por los dias alquilados
			BigDecimal dias = new BigDecimal(diasDiff);
			BigDecimal precio_dias = rs.getBigDecimal(1).multiply(dias);
			
			//Calculamos el precio del combustible multiplicando la capacidad del deposito por el precio por litro
			BigDecimal capacidad = new BigDecimal(rs.getInt(2));
			BigDecimal precio_combustible = rs.getBigDecimal(3).multiply(capacidad);
			
			//Calculamos el precio total de la reserva sumando el precio por combusitble con el precio por alquiler
			BigDecimal precio_total = precio_dias.add(precio_combustible);
			
			//Insertamos la factura total del alquiler
			st = con.prepareStatement("INSERT INTO facturas VALUES (seq_num_fact.nextval, ?, ?)");
			st.setBigDecimal(1, precio_total);
			st.setString(2, nifCliente);
			
			st.executeUpdate();
			
			//Insertamos la linea de factura por dias de alquiler
			st = con.prepareStatement("INSERT INTO lineas_factura VALUES (seq_num_fact.currval, ?, ?)");
			String concepto = dias + " dias de alquiler, vehiculo modelo " + rs.getInt(4);
			st.setString(1, concepto);
			st.setBigDecimal(2, precio_dias);
			st.executeUpdate();
			
			//Insertamos la linea de factura por combustible gastado
			st = con.prepareStatement("INSERT INTO lineas_factura VALUES (seq_num_fact.currval, ?, ?)");
			concepto = "Deposito lleno de " + capacidad + " litros de " + rs.getString(5);
			st.setString(1, concepto);
			st.setBigDecimal(2, precio_combustible);
			st.executeUpdate();
			
			con.commit();
			
			/* A completar por el alumnado... */

			/* ================================= AYUDA R�PIDA ===========================*/
			/*
			 * Algunas de las columnas utilizan tipo numeric en SQL, lo que se traduce en
			 * BigDecimal para Java.
			 * 
			 * Convertir un entero en BigDecimal: new BigDecimal(diasDiff)
			 * 
			 * Sumar 2 BigDecimals: usar metodo "add" de la clase BigDecimal
			 * 
			 * Multiplicar 2 BigDecimals: usar metodo "multiply" de la clase BigDecimal
			 *
			 * 
			 * Paso de util.Date a sql.Date java.sql.Date sqlFechaIni = new
			 * java.sql.Date(sqlFechaIni.getTime());
			 *
			 *
			 * Recuerda que hay casos donde la fecha fin es nula, por lo que se debe de
			 * calcular sumando los dias de alquiler (ver variable DIAS_DE_ALQUILER) a la
			 * fecha ini.
			 */
			
		} catch (SQLException e) {
			// Completar por el alumno
			if (con != null) {
				con.rollback();
			}
			
			
			LOGGER.debug(e.getMessage());

			throw e;

		} finally {
			/* A rellenar por el alumnado*/
			if (rs != null) {
				rs.close();
			}
			
			if (st != null) {
				st.close();
			}
			
			if (con != null) {
				con.close();
			}
		}
	}
}
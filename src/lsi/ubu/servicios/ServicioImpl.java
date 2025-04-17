package lsi.ubu.servicios;

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
		java.sql.Date sqlFechaFin = new java.sql.Date(fechaFin.getTime());
				
		
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
			
			//Comprobaciones previas
			try {
				inexCliente = con.prepareStatement("SELECT 1 FROM clientes WHERE NIF = ?");
				inexCliente.setString(1, nifCliente);
				
				rsCliente = inexCliente.executeQuery();
				
				if(!rsCliente.next()) {
					throw new AlquilerCochesException(AlquilerCochesException.CLIENTE_NO_EXIST);
				}	
			} finally {
				if(rsCliente != null) {
					rsCliente.close();
					inexCliente.close();
				}
			}
			
			//Comprobaciones previas
			try {
				inexMatricula= con.prepareStatement("SELECT 1 FROM vehiculos WHERE matricula = ?");
				inexMatricula.setString(1, matricula);
				
				rsMatricula = inexMatricula.executeQuery();
				
				if(!rsMatricula.next()) {
					throw new AlquilerCochesException(AlquilerCochesException.VEHICULO_NO_EXIST);
				}	
			} finally {
				if(rsMatricula != null) {
					rsMatricula.close();
					inexMatricula.close();
				}
			}

			
			//Comprobaciones previas coche no dispoonible
			try {
				
				
				
				
				dispVehiculo= con.prepareStatement("SELECT fecha_fin FROM reservas WHERE matricula = ?");
				dispVehiculo.setString(1, matricula);
				
				
				rsVehiculo = dispVehiculo.executeQuery();
				
				Date fecha=rsVehiculo.getDate("fecha_fin");
				
				System.out.println(rsVehiculo);
				
				if(!rsVehiculo.next()) {
					throw new AlquilerCochesException(AlquilerCochesException.VEHICULO_OCUPADO);
				}	
				
				
				//if (sqlFechaIni < rsVehiculo.afterLast()) {
				//	throw new AlquilerCochesException(AlquilerCochesException.VEHICULO_OCUPADO);
				//}
			} finally {
				if(rsVehiculo != null) {
					rsVehiculo.close();
					dispVehiculo.close();
				}
			}
			
			st = con.prepareStatement("INSERT INTO reservas (idReserva, Cliente, matricula, fecha_ini, fecha_fin) "
					+ "VALUES(seq_reservas.nextval, ?, ?, ?, ?)");
			st.setString(1, nifCliente);
			st.setString(2, matricula);
			st.setDate(3, sqlFechaIni);
			st.setDate(4, sqlFechaFin);

			st.executeUpdate();
			
			
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
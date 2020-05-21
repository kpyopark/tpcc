package com.codefutures.tpcc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

public class SimpleDriverDelegatorDataSource implements DataSource {

  String url;
  Properties props;

  public SimpleDriverDelegatorDataSource(final String classForDriver, final String jdbcUrl, Properties props) {
    try {
      Class.forName(classForDriver);
      this.url = jdbcUrl;
      this.props = props;
    } catch (final ClassNotFoundException cnfe) {
      throw new RuntimeException("this jdbc Driver["+ classForDriver + "] can't be found in the class loader.");
    }
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    // Not used.
    return null;
  }

  @Override
  public boolean isWrapperFor(final Class<?> arg0) throws SQLException {
    // Not used.
    return false;
  }

  @Override
  public <T> T unwrap(final Class<T> arg0) throws SQLException {
    // Not used.
    return null;
  }

  @Override
  public Connection getConnection() throws SQLException {
    return DriverManager.getConnection(url, this.props);
  }

  @Override
  public Connection getConnection(final String user, final String password) throws SQLException {
    return DriverManager.getConnection(url, user, password);
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    // not used.
    return null;
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    // not used.
    return 0;
  }

  @Override
  public void setLogWriter(final PrintWriter arg0) throws SQLException {
    // not used.
  }

  @Override
  public void setLoginTimeout(final int arg0) throws SQLException {
    // not used.
  }
  

}
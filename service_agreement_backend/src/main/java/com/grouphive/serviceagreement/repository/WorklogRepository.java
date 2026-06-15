package com.grouphive.serviceagreement.repository;

import com.grouphive.serviceagreement.model.Worklog;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class WorklogRepository {
  private final JdbcTemplate jdbc;

  public WorklogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Worklog> findByOrder(String orderNumber) {
    return jdbc.query(
        "SELECT * FROM worklogs WHERE order_number = ? ORDER BY booked_at DESC, id DESC",
        this::mapRow,
        orderNumber
    );
  }

  public List<Worklog> findByOrderAndTicket(String orderNumber, String ticketNumber) {
    return jdbc.query(
        """
        SELECT * FROM worklogs
        WHERE order_number = ? AND ticket_number = ?
        ORDER BY booked_at DESC, id DESC
        """,
        this::mapRow,
        orderNumber,
        ticketNumber
    );
  }

  public List<Worklog> findAll() {
    return jdbc.query(
        "SELECT * FROM worklogs ORDER BY booked_at DESC, id DESC",
        this::mapRow
    );
  }

  public long insert(
      String orderNumber,
      String customer,
      String ticketNumber,
      Instant bookedAt,
      String person,
      BigDecimal hours,
      String comment
  ) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var ps = connection.prepareStatement(
          """
          INSERT INTO worklogs (order_number, customer, ticket_number, booked_at, person, hours, comment)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          """,
          new String[] {"id"}
      );
      ps.setString(1, orderNumber);
      ps.setString(2, customer);
      ps.setString(3, ticketNumber);
      ps.setTimestamp(4, Timestamp.from(bookedAt));
      ps.setString(5, person);
      ps.setBigDecimal(6, hours);
      ps.setString(7, comment);
      return ps;
    }, keyHolder);

    Number key = keyHolder.getKey();
    return key == null ? 0 : key.longValue();
  }

  public boolean update(
      long id,
      String orderNumber,
      String ticketNumber,
      String customer,
      String person,
      BigDecimal hours,
      String comment
  ) {
    int count = jdbc.update(
        """
        UPDATE worklogs
        SET customer = ?, person = ?, hours = ?, comment = ?, updated_at = now()
        WHERE id = ? AND order_number = ? AND ticket_number = ?
        """,
        customer,
        person,
        hours,
        comment,
        id,
        orderNumber,
        ticketNumber
    );
    return count > 0;
  }

  private Worklog mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new Worklog(
        rs.getLong("id"),
        rs.getString("order_number"),
        rs.getString("customer"),
        rs.getString("ticket_number"),
        rs.getTimestamp("booked_at").toInstant(),
        rs.getString("person"),
        rs.getBigDecimal("hours"),
        rs.getString("comment")
    );
  }
}

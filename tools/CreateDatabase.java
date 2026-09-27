import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Setup helper used by setup-and-run.ps1 (run with: java -cp mssql-jdbc.jar tools/CreateDatabase.java ...).
 * Connects to the "master" database, creates the application database when missing and reports its state:
 *   CREATED  - database was just created (tables must be initialised)
 *   EMPTY    - database exists but has no ArtistikCity tables yet
 *   READY    - database exists and already contains the tables
 * Arguments: <jdbc-url-to-master> <user|-> <password|-> <database-name>
 */
public class CreateDatabase {
    public static void main(String[] args) throws Exception {
        String url = args[0];
        String user = args.length > 1 && !"-".equals(args[1]) ? args[1] : null;
        String password = args.length > 2 && !"-".equals(args[2]) ? args[2] : null;
        String db = args.length > 3 ? args[3] : "artistikcity";
        if (!db.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid database name: " + db);
        }
        try (Connection c = user == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, password);
             Statement st = c.createStatement()) {
            boolean exists;
            try (ResultSet rs = st.executeQuery("SELECT DB_ID('" + db + "')")) {
                rs.next();
                exists = rs.getObject(1) != null;
            }
            if (!exists) {
                st.execute("CREATE DATABASE [" + db + "]");
                System.out.println("CREATED");
                return;
            }
            try (ResultSet rs = st.executeQuery("SELECT OBJECT_ID('[" + db + "].dbo.users')")) {
                rs.next();
                System.out.println(rs.getObject(1) == null ? "EMPTY" : "READY");
            }
        }
    }
}

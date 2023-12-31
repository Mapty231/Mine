package me.tye.mine;

import me.tye.mine.clans.Claim;
import me.tye.mine.clans.Clan;
import me.tye.mine.clans.Member;
import me.tye.mine.clans.Perm;
import me.tye.mine.errors.FatalDatabaseException;
import me.tye.mine.utils.MineCacheMap;
import me.tye.mine.utils.TempConfigsStore;
import me.tye.mine.utils.Unloader;
import org.bukkit.Material;
import org.codehaus.plexus.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.*;

import static me.tye.mine.utils.Util.handleFatalException;

public class Database {

public static final Map<UUID, Clan> clansCache = Collections.synchronizedMap(new MineCacheMap<>()); //try & solve these warnings?
public static final Map<UUID, Claim> claimsCache = Collections.synchronizedMap(new MineCacheMap<>());
public static final Map<UUID, Perm> permsCache = Collections.synchronizedMap(new MineCacheMap<>());
public static final Map<UUID, Member> memberCache = Collections.synchronizedMap(new MineCacheMap<>());


private static boolean initiated = false;
private static Connection dbConnection;

/**
 Creates the connection to the database & creates the required tables if they don't exist.
 * @throws SQLException If there was an error interacting with the database.
 */
public static void init() throws SQLException {
  if (initiated) return;

  String databasePath = FileUtils.removeExtension(TempConfigsStore.database.getAbsolutePath()) + ".db";

  String databaseUrl = "jdbc:sqlite:" + databasePath;

  dbConnection = DriverManager.getConnection(databaseUrl);

  //Create default tables;
  String clanTable = """
        CREATE TABLE IF NOT EXISTS clans (
        clanID TEXT NOT NULL PRIMARY KEY,
              
        name TEXT NOT NULL,
        description TEXT NOT NULL,
        renderingOutline TEXT NOT NULL
              
        ) WITHOUT ROWID;
        """;

  String PermsTable = """
        CREATE TABLE IF NOT EXISTS perms (
        permID TEXT NOT NULL PRIMARY KEY,
              
        name TEXT NOT NULL,
        description TEXT NOT NULL,
              
              
        clanID TEXT,
        memberID TEXT,
        FOREIGN KEY (clanID) REFERENCES clans (clanID) ON DELETE CASCADE,
        FOREIGN KEY (memberID) REFERENCES membersID (memberID) ON DELETE CASCADE,
        
        CHECK (clanID != NULL OR memberID != NULL)
              
        ) WITHOUT ROWID;
        """;

  String claimsTable = """
        CREATE TABLE IF NOT EXISTS claims (
        claimID TEXT NOT NULL PRIMARY KEY,
              
        worldName TEXT NOT NULL,
              
        X1 REAL NOT NULL,
        X2 REAL NOT NULL,
        Y1 REAL NOT NULL,
        Y2 REAL NOT NULL,
        Z1 REAL NOT NULL,
        Z2 REAL NOT NULL,
        
        clanID TEXT NOT NULL,
        FOREIGN KEY (clanID) REFERENCES clans (clanID) ON DELETE CASCADE

        ) WITHOUT ROWID;
        """;

  String memberTable = """
        CREATE TABLE IF NOT EXISTS members (
        memberID TEXT NOT NULL PRIMARY KEY,
              
        clanPermID TEXT,
              
        clanID TEXT,
        FOREIGN KEY (clanID) REFERENCES clans (clanID) ON DELETE SET NULL
              
        ) WITHOUT ROWID;
        """;

  String claimedChunks = """
        CREATE TABLE IF NOT EXISTS claimedChunks (
        
        claimID TEXT NOT NULL,
        chunkKey INTEGER NOT NULL,
        
        FOREIGN KEY (claimID) REFERENCES claims (claimID) ON DELETE CASCADE
        );
        """;


  Statement statement = dbConnection.createStatement();
  dbConnection.setAutoCommit(false);

  statement.execute(clanTable);
  statement.execute(claimsTable);
  statement.execute(memberTable);
  statement.execute(PermsTable);
  statement.execute(claimedChunks);

  dbConnection.commit();
  dbConnection.setAutoCommit(true);

  Unloader.init();

  initiated = true;
}

/**
 Closes the connection to the database.<br>
 The {@link #init()} method should be run to reestablish a new connection.
 */
public static void close() {
  killConnection();
  dbConnection = null;
  initiated = false;
}

/**
 Gets the connection to the database.<br>
 <b>Don't use this method with auto closable. The connection to the database should stay open.</b>
 * @return The connection to the database.
 * @throws FatalDatabaseException If a connection to the database couldn't be established.
 */
private static @NotNull Connection getDbConnection() throws FatalDatabaseException {
  try {
    //Attempts to reconnect to the database if it has lost connection
    if (dbConnection.isClosed()) {
      String databasePath = FileUtils.removeExtension(TempConfigsStore.database.getAbsolutePath())+".db";
      String databaseUrl = "jdbc:sqlite:"+databasePath;

      dbConnection = DriverManager.getConnection(databaseUrl);
    }

    return dbConnection;

  } catch (SQLException e) {

    //If there was an error determining if the database lost connection then try to establish a new connection.
    try {
      dbConnection = null;

      String databasePath = FileUtils.removeExtension(TempConfigsStore.database.getAbsolutePath())+".db";
      String databaseUrl = "jdbc:sqlite:"+databasePath;

      dbConnection = DriverManager.getConnection(databaseUrl);
      return dbConnection;

    } catch (SQLException ex) {
      throw handleFatalException(ex);
    }

  }
}

/**
 Kills the connection to the database.
 */
private static void killConnection() {
  try {
    if (!dbConnection.getAutoCommit()) {
      dbConnection.commit();
    }

    dbConnection.close();

    //Doesn't throw fatal error since this will be called before fatal database errors.
  } catch (SQLException ignore) {}
}

/**
 <b>DO NOT USE THIS METHOD.</b><br>
 This method will remove all the data from the database.<br>
 <br>
 This is intended for development only.
 @return True if the tables were dropped.
 */
public static boolean purge() {
  try {
    killConnection();

    Connection newConnection = getDbConnection();
    newConnection.setAutoCommit(false);

    Statement statement = newConnection.createStatement();
    statement.execute("DROP TABLE clans");
    statement.execute("DROP TABLE members");
    statement.execute("DROP TABLE perms");
    statement.execute("DROP TABLE claims");
    statement.execute("DROP TABLE claimedChunks");

    newConnection.commit();
    newConnection.close();

    claimsCache.clear();
    memberCache.clear();
    clansCache.clear();
    memberCache.clear();

    initiated = false;
    init();

    return true;
  } catch (SQLException e) {
    e.printStackTrace();

    return false;
  }
}


/**
 Gets the result of the given query from the database.
 * @param query The given query.
 * @return The result set from the database.
 * @throws FatalDatabaseException If there was an error querying the database.
 */
private static @NotNull ResultSet getResult(@NotNull String query) throws FatalDatabaseException {
  try {
    Connection dbConnection = getDbConnection();
    Statement statement = dbConnection.createStatement();
    return statement.executeQuery(query);

  } catch (SQLException e) {
    killConnection();
    throw handleFatalException(e);
  }
}


/**
 Checks if the database has a response for a query.
 * @param query The given query.
 * @return True if the database responded with a populated result set. False otherwise.
 * @throws FatalDatabaseException If there was an error querying the database.
 */
private static boolean hasResult(@NotNull String query) throws FatalDatabaseException {
  try {
    ResultSet result = getResult(query);
    return result.next();

  } catch (SQLException e) {
    killConnection();
    throw handleFatalException(e);
  }
}

/**
 Creates a where statement that matches all the given uuids in the given column.
 * @param column The given column.
 * @param uuids The given uuids. This should <b>not</b> be empty.
 * @return The where statement that will match all the uuids.
 */
private static @NotNull String createWhere(@NotNull String column, @NotNull Collection<UUID> uuids) {
  StringBuilder where = new StringBuilder("WHERE ");

  for (UUID uuid : uuids) {
    String stringUUID = uuid.toString();
    where.append("\"")
         .append(column)
         .append("\"")
         .append(" == ")
         .append("\"")
         .append(stringUUID)
         .append("\"")
         .append(" OR ");
  }

  return where.substring(0, where.length()-4);
}

/**
 Adds a member to the cache.
 * @param memberID The uuid of the member.
 */
public static void cacheMember(@NotNull UUID memberID) {
  Member member = Database.getMember(memberID);
  if (member == null) return;

  memberCache.put(memberID, member);
}

/**
 Checks if a clan with the given UUID already exists.
 * @param clanID The UUID to check.
 * @return True if the clan exists.
 */
public static boolean clanExists(@NotNull UUID clanID) {
  if (clansCache.containsKey(clanID)) return true;

  return exists("clanID", "clans", clanID);
}

/**
 Checks if a claim with the given UUID already exists.
 * @param claimID The UUID to check.
 * @return True if the claim exists.
 */
public static boolean claimExists(@NotNull UUID claimID) {
  if (claimsCache.containsKey(claimID)) return true;

  return exists("claimID", "claims", claimID);
}

/**
 Checks if a member with the given UUID already exists.
 * @param memberID The UUID to check.
 * @return True if the member exists.
 */
public static boolean memberExists(@NotNull UUID memberID) {
  if (memberCache.containsKey(memberID)) return true;

  return exists("memberID", "members", memberID);
}

/**
 Checks if a perm with the given UUID already exists.
 * @param permID The UUID to check.
 * @return True if the perm exists.
 */
public static boolean permExists(@NotNull UUID permID) {
  if (permsCache.containsKey(permID)) return true;

  return exists("permID", "perms", permID);
}


/**
 Checks if the given uuid exists in the given column, in the given table.
 * @param column The given column.
 * @param table The given table.
 * @param uuid The given uuid.
 * @return True if the uuid is taken.
 */
private static boolean exists(@NotNull String column, @NotNull String table, @NotNull UUID uuid) {
  return hasResult("SELECT "+column+" FROM "+table+" WHERE \""+column+"\" == \""+uuid+"\";");
}


/**
 Gets a member from the database.
 * @param memberID The uuid of the member.
 * @return The member with this uuid. If the member doesn't exist null will be returned.
 * @throws FatalDatabaseException If there was an error querying the database.
 */
public static @Nullable Member getMember(@NotNull UUID memberID) throws FatalDatabaseException {
  if (!memberExists(memberID)) return null;

  Member member;

  try (ResultSet memberData = getResult(
      "SELECT * FROM members WHERE \"memberID\" == \""+memberID+"\";"
  )) {

    memberData.next();

    String rawClanID = memberData.getString("clanID");
    String rawClanPermID = memberData.getString("clanPermID");

    UUID clanID = null;
    UUID clanPermID = null;


    if (rawClanID != null) {
      clanID = UUID.fromString(rawClanID);
    }

    if (rawClanPermID != null) {
      clanPermID = UUID.fromString(rawClanPermID);
    }

    member = new Member(memberID, clanID, clanPermID);

  } catch (SQLException | IllegalArgumentException e) {
    killConnection();
    throw handleFatalException(e);
  }

  memberCache.put(memberID, member);
  return member;
}

/**
 Gets a clan from the database.
 * @param clanID The uuid of the clan.
 * @return The clan with the given uuid. If the clan doesn't exist null will be returned.
 * @throws FatalDatabaseException If there was an error querying the database.
 */
public static @Nullable Clan getClan(@NotNull UUID clanID) throws FatalDatabaseException {
  if (!clanExists(clanID)) return null;

  Clan clan;

  try (ResultSet clanData = getResult(
      "SELECT * FROM clans WHERE \"clanID\" == \""+clanID+"\";"
  )) {

    clanData.next();

    String clanName = clanData.getString("name");
    String clanDescription = clanData.getString("description");
    Material renderingOutline = Material.valueOf(clanData.getString("renderingOutline").toUpperCase());

    //Gets the UUIDS of all the claims
    Collection<UUID> claimIDs = new ArrayList<>();
    ResultSet claims = getResult("SELECT claimID FROM claims WHERE \"clanID\" == \""+clanID+"\";");
    while (claims.next()) {
      claimIDs.add(UUID.fromString(claims.getString("claimID")));
    }

    //Gets the UUIDS of all the members
    Collection<UUID> memberIDs = new ArrayList<>();
    ResultSet members = getResult("SELECT memberID FROM members WHERE \"clanID\" == \""+clanID+"\";");
    while (members.next()) {
      memberIDs.add(UUID.fromString(members.getString("memberID")));
    }

    //Gets the UUIDS of all the members
    Collection<UUID> permIDs = new ArrayList<>();
    ResultSet perms = getResult("SELECT permID FROM perms WHERE \"clanID\" == \""+clanID+"\";");
    while (perms.next()) {
      permIDs.add(UUID.fromString(perms.getString("permID")));
    }

    clan = new Clan(clanID, claimIDs ,memberIDs, permIDs, clanName, clanDescription, renderingOutline);

  } catch (SQLException | IllegalArgumentException e) {
    killConnection();
    throw handleFatalException(e);
  }

  clansCache.put(clanID, clan);
  return clan;
}

/**
 Gets a claim from the database & loads it into the cache.
 * @param claimID The uuid of the claim
 * @return The claim with this uuid. If the claim doesn't exist null will be returned.
 * @throws FatalDatabaseException If there was an error querying the database.
 */
public static @Nullable Claim getClaim(@NotNull UUID claimID) throws FatalDatabaseException {
  if (!claimExists(claimID)) return null;

  Claim claim;

  try (ResultSet claimData = getResult(
      "SELECT * FROM claims WHERE \"claimID\" == \""+claimID+"\";"
  )) {

    claimData.next();

    String worldName = claimData.getString("worldName");
    int X1 = claimData.getInt("X1");
    int X2 = claimData.getInt("X2");
    int Y1 = claimData.getInt("Y1");
    int Y2 = claimData.getInt("Y2");
    int Z1 = claimData.getInt("Z1");
    int Z2 = claimData.getInt("Z2");
    UUID clanID = UUID.fromString(claimData.getString("clanID"));


    //Gets all the chunks that this claim is in
    try (ResultSet chunks = getResult(
        "SELECT chunkKey FROM claimedChunks WHERE \"claimID\" == \""+claimID+"\""
    )) {

      HashSet<Long> claimInChunks = new HashSet<>();

      while (chunks.next()) {
        claimInChunks.add(chunks.getLong("chunkKey"));
      }

      claim = new Claim(clanID, claimID, worldName, X1, X2, Y1, Y2, Z1, Z2, claimInChunks);
    }

  } catch (SQLException | IllegalArgumentException e) {
    killConnection();
    throw handleFatalException(e);
  }

  claimsCache.put(claimID, claim);
  return claim;
}

/**
 Gets a perm from the database & loads it into the cache.
 * @param permId The uuid of the perm to get
 * @return The perm with this uuid. If the perm doesn't exist null will be returned.
 * @throws FatalDatabaseException If there was an error querying the database.
 */
public static @Nullable Perm getPerm(@NotNull UUID permId) throws FatalDatabaseException {
  if (permExists(permId)) return null;

  Perm perm;

  try (ResultSet permData = getResult(
      "SELECT * FROM perms WHERE \"permID\" == \""+permId+"\";"
  )) {

    permData.next();

    String permName = permData.getString("name");
    String permDescription = permData.getString("description");

    perm = new Perm(permId, permName, permDescription);

  } catch (SQLException | IllegalArgumentException e) {
    killConnection();
    throw handleFatalException(e);
  }

  permsCache.put(perm.getPermID(), perm);
  return perm;
}

/**
 Writes a member to the database & puts it into the cache.<br>
 You can use {@link #memberExists(UUID)} to check if the member exists before creating a new one.
 * @param memberID The uuid of the member to create.
 * @throws FatalDatabaseException If there was an error accessing the database.
 */
public static void createMember(@NotNull UUID memberID) throws FatalDatabaseException {
  if (memberExists(memberID)) return;

  try {
    Connection dbConnection = getDbConnection();
    dbConnection.setAutoCommit(true);

    PreparedStatement statement = dbConnection.prepareStatement(
        "INSERT INTO members (memberID, clanID, clanPermID) VALUES(?,?,?)"
    );

    statement.setString(1, memberID.toString());
    statement.setNull(2, Types.VARCHAR);
    statement.setNull(3, Types.VARCHAR);

    statement.execute();

  } catch (SQLException e) {
    killConnection();
    throw handleFatalException(e);
  }

  memberCache.put(memberID, new Member(memberID, null, null));
}

/**
 Writes a clan to the database & puts it into the cache.<br>
 You can use {@link #clanExists(UUID)} to check if the clan exists before creating a new one.
 * @param newClan The new clan to write to the database.
 * @throws FatalDatabaseException If there was an error accessing the database.
 */
public static void createClan(@NotNull Clan newClan) throws FatalDatabaseException {
  if (clanExists(newClan.getClanID())) return;

  try {
    Connection dbConnection = getDbConnection();
    dbConnection.setAutoCommit(false);

    //create the clan
    PreparedStatement clanCreate = dbConnection.prepareStatement(
        "INSERT INTO clans (clanID, name, description, renderingOutline) VALUES(?,?,?,?)");

    clanCreate.setString(1, newClan.getClanID().toString());
    clanCreate.setString(2, newClan.getName());
    clanCreate.setString(3, newClan.getDescription());
    clanCreate.setString(4, newClan.getOutlineMaterial().toString());

    clanCreate.executeUpdate();


    //adds the members to the clan.
    Statement statement = dbConnection.createStatement();
    statement.execute("UPDATE members SET clanID = \""+newClan.getClanID()+"\" "+createWhere("memberID", newClan.getMemberUUIDs()));


    //adds all the claims to the clan.
    for (Claim claim : newClan.getClanClaims()) {
      PreparedStatement claimCreate = dbConnection.prepareStatement("""
        INSERT INTO claims (claimID, worldName, X1, X2, Y1, Y2, Z1, Z2, clanID) VALUES(?,?,?,?,?,?,?,?,?)
        """);

      claimCreate.setString(1, claim.getClaimID().toString());
      claimCreate.setString(2, claim.getWorldName());
      claimCreate.setDouble(3, claim.getX1());
      claimCreate.setDouble(4, claim.getX2());
      claimCreate.setDouble(5, claim.getY1());
      claimCreate.setDouble(6, claim.getY2());
      claimCreate.setDouble(7, claim.getZ1());
      claimCreate.setDouble(8, claim.getZ2());
      claimCreate.setString(9, newClan.getClanID().toString());

      claimCreate.executeUpdate();
    }

    dbConnection.commit();
    dbConnection.setAutoCommit(true);

  } catch (SQLException e) {
    killConnection();
    throw handleFatalException(e);
  }

  clansCache.put(newClan.getClanID(), newClan);
}

/**
 Writes a new claim to the database & puts it into the cache.<br>
 You can use {@link #claimExists(UUID)} to check if the clan exists before creating a new one.
 * @param newClaim The new claim to write to the database.
 * @throws FatalDatabaseException If there was an error accessing the database.
 */
public static void createClaim(@NotNull Claim newClaim) throws FatalDatabaseException {
  if (claimExists(newClaim.getClaimID())) return;

  try {
    Connection dbConnection = getDbConnection();
    dbConnection.setAutoCommit(false);

    PreparedStatement createClaim = dbConnection.prepareStatement("""
        INSERT INTO claims (claimID, worldName, X1, X2, Y1, Y2, Z1, Z2, clanID) VALUES(?,?,?,?,?,?,?,?,?)
        """);

    createClaim.setString(1, newClaim.getClaimID().toString());
    createClaim.setString(2, newClaim.getWorldName());
    createClaim.setDouble(3, newClaim.getX1());
    createClaim.setDouble(4, newClaim.getX2());
    createClaim.setDouble(5, newClaim.getY1());
    createClaim.setDouble(6, newClaim.getY2());
    createClaim.setDouble(7, newClaim.getZ1());
    createClaim.setDouble(8, newClaim.getZ2());
    createClaim.setString(9, newClaim.getClanID().toString());

    createClaim.executeUpdate();


    for (Long chunkKey : newClaim.getChunkKeys()) {
      PreparedStatement claimedChunks = dbConnection.prepareStatement("""
        INSERT INTO claimedChunks (claimID, chunkKey) VALUES(?,?)
        """);

      claimedChunks.setString(1, newClaim.getClaimID().toString());
      claimedChunks.setLong(2, chunkKey);
      claimedChunks.executeUpdate();
    }

    dbConnection.commit();

  } catch (SQLException e) {
    killConnection();
    throw handleFatalException(e);
  }

  claimsCache.put(newClaim.getClaimID(), newClaim);
}


/**
 Updates the database entry & cache for an existing member.<br>
 You can use {@link #memberExists(UUID)} to check if the member exists before updating them.
 * @param updatedMember The member to update.
 * @throws FatalDatabaseException If there was an error accessing the database.
 */
public static void updateMember(@NotNull Member updatedMember) throws FatalDatabaseException {
  if (!memberExists(updatedMember.getMemberID())) return;

  try {
    Connection dbConnection = getDbConnection();

    PreparedStatement clanUpdate = dbConnection.prepareStatement("""
            UPDATE members SET
            clanPermID = ?,
            clanID = ?,
            WHERE memberID == ?;
            """);


    UUID clanPermID = updatedMember.getClanPermID();
    if (clanPermID == null) {
      clanUpdate.setNull(1, Types.NULL);
    }
    else {
      clanUpdate.setString(1, clanPermID.toString());
    }

    UUID clanID = updatedMember.getClanID();
    if (clanID == null) {
      clanUpdate.setNull(2, Types.NULL);
    }
    else {
      clanUpdate.setString(2, clanID.toString());
    }

    clanUpdate.setString(3, updatedMember.getMemberID().toString());

    clanUpdate.executeUpdate();

  } catch (SQLException e) {
    killConnection();
    throw handleFatalException(e);
  }

  memberCache.put(updatedMember.getMemberID(), updatedMember);
}


/**
 Updates the database entry & cache for an existing clan.<br>
 You can use {@link #clanExists(UUID)} to check if the clan exists before updating one.
 * @param updatedClan The clan to update.
 * @throws FatalDatabaseException If there was an error accessing the database.
 */
public static void updateClan(@NotNull Clan updatedClan) throws FatalDatabaseException {
  if (!clanExists(updatedClan.getClanID())) return;

  try {
    Connection dbConnection = getDbConnection();
    dbConnection.setAutoCommit(false);

    //create the clan
    PreparedStatement clanUpdate = dbConnection.prepareStatement("""
            UPDATE clans SET
            name = ?,
            description = ?,
            renderingOutline = ?
            WHERE clanID == ?;
            """);

    clanUpdate.setString(1, updatedClan.getName());
    clanUpdate.setString(2, updatedClan.getDescription());
    clanUpdate.setString(3, updatedClan.getClanID().toString());
    clanUpdate.setString(3, updatedClan.getOutlineMaterial().toString());

    clanUpdate.executeUpdate();


    //adds the members to the clan.
    Statement statement = dbConnection.createStatement();
    statement.execute("UPDATE members SET clanID = \""+updatedClan.getClanID()+"\" "+createWhere("memberID", updatedClan.getMemberUUIDs()));



    //adds all the claims to the clan.
    for (Claim claim : updatedClan.getClanClaims()) {

      //If the claim exist update it's values then continue.
      if (claimExists(claim.getClaimID())) {
        PreparedStatement claimUpdate = dbConnection.prepareStatement("""
            UPDATE claims SET
            worldName = ?,
            X1 = ?,
            X1 = ?,
            Y1 = ?,
            Y1 = ?,
            Z1 = ?,
            Z1 = ?,
            Z2 = ?
            WHERE claimID = ?
            """);

        claimUpdate.setString(1, claim.getWorldName());
        claimUpdate.setDouble(2, claim.getX1());
        claimUpdate.setDouble(3, claim.getX2());
        claimUpdate.setDouble(4, claim.getY1());
        claimUpdate.setDouble(5, claim.getY2());
        claimUpdate.setDouble(6, claim.getZ1());
        claimUpdate.setDouble(7, claim.getZ2());
        claimUpdate.setString(8, updatedClan.getClanID().toString());
        claimUpdate.setString(9, claim.getClaimID().toString());

        clanUpdate.execute();
        continue;
      }

      PreparedStatement claimCreate = dbConnection.prepareStatement("""
            INSERT INTO claims (claimID, worldName, X1, X2, Y1, Y2, Z1, Z2, clanID) VALUES(?,?,?,?,?,?,?,?,?)
            """);

      claimCreate.setString(1, claim.getClaimID().toString());
      claimCreate.setString(2, claim.getWorldName());
      claimCreate.setDouble(3, claim.getX1());
      claimCreate.setDouble(4, claim.getX2());
      claimCreate.setDouble(5, claim.getY1());
      claimCreate.setDouble(6, claim.getY2());
      claimCreate.setDouble(7, claim.getZ1());
      claimCreate.setDouble(8, claim.getZ2());
      claimCreate.setString(9, updatedClan.getClanID().toString());

      claimCreate.executeUpdate();
    }

    dbConnection.commit();
    dbConnection.setAutoCommit(true);

  } catch (SQLException e) {
    killConnection();
    throw handleFatalException(e);
  }

  clansCache.put(updatedClan.getClanID(), updatedClan);
}

/**
 * @return The chunks keys of every claim.
 * @throws FatalDatabaseException If there was an error querying the database.
 */
public static @NotNull Collection<Long> getChunkKeys() throws FatalDatabaseException {
  try (ResultSet databaseChunkKeys = getResult(
      "SELECT DISTINCT (chunkKey) FROM claimedChunks"
  )) {

    ArrayList<Long> chunkKeys = new ArrayList<>();

    while (databaseChunkKeys.next()) {
      chunkKeys.add(databaseChunkKeys.getLong("chunkKey"));
    }

    return chunkKeys;

  } catch (SQLException e) {
    killConnection();
    throw handleFatalException(e);
  }
}

/**
 * @param chunkKey The chunk key.
 * @return Claims that are in the chunk represented by the given chunk key. If the database doesn't contain any chunks with this key then an empty list will be returned
 * @throws FatalDatabaseException If there was an error querying the database.
 */
public static @NotNull List<Claim> getClaims(@NotNull Long chunkKey) throws FatalDatabaseException {
  try (ResultSet claimIDs = getResult(
      "SELECT DISTINCT (claimID) FROM claimedChunks WHERE \"chunkKey\" == \""+chunkKey+"\""
  )) {

    ArrayList<Claim> claims = new ArrayList<>();

    while (claimIDs.next()) {
      UUID claimID = UUID.fromString(claimIDs.getString("claimID"));
      claims.add(getClaim(claimID));
    }

    return claims;

  } catch (SQLException e) {
    killConnection();
    throw handleFatalException(e);
  }
}

/**
 * @return All the claims from the database.
 * @throws FatalDatabaseException If there was an error querying the database.
 */
public static @NotNull List<Claim> getClaims() throws FatalDatabaseException {
  ArrayList<Claim> claims = new ArrayList<>();

  try (ResultSet claimData = getResult(
      "SELECT * FROM claims"
  )) {

    //goes through all the claims in the database.
    while (claimData.next()) {

      String worldName = claimData.getString("worldName");
      int X1 = claimData.getInt("X1");
      int X2 = claimData.getInt("X2");
      int Y1 = claimData.getInt("Y1");
      int Y2 = claimData.getInt("Y2");
      int Z1 = claimData.getInt("Z1");
      int Z2 = claimData.getInt("Z2");
      UUID clanID = UUID.fromString(claimData.getString("clanID"));
      UUID claimID = UUID.fromString(claimData.getString("claimID"));


      //Gets all the chunks that this claim is in
      try (ResultSet chunks = getResult(
          "SELECT chunkKey FROM claimedChunks WHERE \"claimID\" == \""+claimID+"\""
      )) {

        HashSet<Long> claimInChunks = new HashSet<>();

        while (chunks.next()) {
          claimInChunks.add(chunks.getLong("chunkKey"));
        }

        claims.add(new Claim(clanID, claimID, worldName, X1, X2, Y1, Y2, Z1, Z2, claimInChunks));
      }

    }

    return claims;

  } catch (SQLException e) {
    killConnection();
    throw handleFatalException(e);
  }
}

/**
 * @return All the members from the database.
 * @throws FatalDatabaseException If there was an error querying the database.
 */
public static @NotNull List<Member> getMembers() throws FatalDatabaseException {
  ArrayList<Member> members = new ArrayList<>();

  try (ResultSet memberData = getResult(
      "SELECT * FROM members"
  )) {

    //goes through all the claims in the database.
    while (memberData.next()) {

      UUID memberID = UUID.fromString(memberData.getString("memberID"));
      String rawClanID = memberData.getString("clanID");
      String rawClanPermID = memberData.getString("clanPermID");

      UUID clanID = null;
      UUID clanPermID = null;


      if (rawClanID != null) {
        clanID = UUID.fromString(rawClanID);
      }

      if (rawClanPermID != null) {
        clanPermID = UUID.fromString(rawClanPermID);
      }

      members.add(new Member(memberID, clanID, clanPermID));
    }

    return members;

  } catch (SQLException e) {
    killConnection();
    throw handleFatalException(e);
  }
}
}

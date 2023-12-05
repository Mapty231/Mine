package me.tye.mine.clans;

import me.tye.mine.Database;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static me.tye.mine.Mine.onlineMembers;

public class Member {

private final @NotNull UUID memberID;

private @Nullable UUID clanPermID;
private @Nullable UUID clanID;


/**
 Gets the member from a player UUID.
 * @param playerId The UUID of a player.
 * @return The Member class for this player, or null if the member can't be gotten form the database.
 */
public static @Nullable Member getMember(@NotNull UUID playerId) {
  //If the player is a member & is online then gets the member object from the HashMap.
  if (onlineMembers.containsKey(playerId)) {
    return onlineMembers.get(playerId);
  }

  //If the member isn't online but exists, get them from the database & load them.
  if (Database.memberExists(playerId)) {
    Member member = Database.getMember(playerId);
    onlineMembers.put(playerId, member);
    return member;
  }

  return null;
}

/**
 Creates a new member for the given player uuid. Member objects are tied to players by their uuid.
 */
public static void createMember(@NotNull UUID playerId) {
  Member member = new Member(playerId, null, null);
  Database.createMember(playerId);
  onlineMembers.put(playerId, member);
}

/**
 If the given ID is of an existing member, then put the member into the cache.
 * @param memberID The uuid of the member.
 */
public static void registerMember(@NotNull UUID memberID) {
  Member member = Database.getMember(memberID);
  if (member == null) return;

  onlineMembers.put(memberID, member);
}


/**
 This method isn't intended for general use. Use {@link #getMember(UUID)} to get a member from their player ID.
 @param memberID The uuid of the member.
 @param clanID The clan uuid of the member.
 @param clanPermID The perm uuid for the members perms within the clan.
 */
public Member(@NotNull UUID memberID, @Nullable UUID clanID, @Nullable UUID clanPermID) {
  this.memberID = memberID;
  this.clanPermID = clanPermID;
  this.clanID = clanID;
}

/**
 * @return True if this member is already in a clan.
 */
public boolean isInClan() {
  return getClanID() != null;
}

/**
 * @return The offline player with this UUID
 */
public @NotNull OfflinePlayer getOfflinePlayer() {
  return Bukkit.getOfflinePlayer(getMemberID());
}

/**
 * @return The online player, or null if the player is offline.
 */
public @Nullable Player getPlayer() {
  return Bukkit.getPlayer(getMemberID());
}


public void renderNearbyClaims(int radius) {
  Player player = getPlayer();
  if (player == null) return;

  Location location = player.getLocation();
  Chunk chunk = location.getChunk();
  chunk.getChunkKey();
}


public @NotNull UUID getMemberID() {
  return memberID;
}

public @Nullable UUID getClanPermID() {
  return clanPermID;
}

public @Nullable UUID getClanID() {
  return clanID;
}
}

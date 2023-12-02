package me.tye.mine;

import me.tye.mine.clans.Member;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoin implements Listener {

@EventHandler
public static void playerJoin(PlayerJoinEvent e) {
  Player player = e.getPlayer();


  if (Database.memberExists(player.getUniqueId())) {
    Member.registerMember(player.getUniqueId());
    return;
  }

  Member.createMember(player.getUniqueId());
}
}

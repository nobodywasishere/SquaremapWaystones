package net.eowyn.squaremapwaystones;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wraith.fwaystones.FabricWaystones;
import wraith.fwaystones.access.WaystoneValue;
import wraith.fwaystones.integration.event.WaystoneEvents;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

public class SquaremapWaystones implements ModInitializer {
  public static final String MOD_ID = "squaremapwaystones";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  private static final Key WAYSTONES_LAYER = Key.of(FabricWaystones.MOD_ID);
  private static final Key WAYSTONES_ICON_KEY = Key.of(FabricWaystones.MOD_ID + "_icon");
  private static final int WAYSTONES_ICON_SIZE = 20;
  private static final SimpleLayerProvider provider = SimpleLayerProvider.builder("Waystones")
                                                                         .showControls(true).defaultHidden(false)
                                                                         .layerPriority(5).zIndex(250).build();

  @Override
  public void onInitialize() {
    ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStart);
    WaystoneEvents.REMOVE_WAYSTONE_EVENT.register(this::onRemove);
    WaystoneEvents.DISCOVER_WAYSTONE_EVENT.register(this::onDiscover);
    WaystoneEvents.RENAME_WAYSTONE_EVENT.register(this::onRename);
    WaystoneEvents.FORGET_ALL_WAYSTONES_EVENT.register(player -> {
      if (FabricWaystones.WAYSTONE_STORAGE != null) {
        FabricWaystones.WAYSTONE_STORAGE.getAllHashes().forEach(this::onRemove);
      }
    });
  }

  private void onRename(String hash) {
    onRemove(hash);
    addWaypoint(hash);
  }

  private void onDiscover(String hash) {
    addWaypoint(hash);
  }

  private void onRemove(String hash) {
    provider.removeMarker(hashToKey(hash));
  }

  private void onServerStart(MinecraftServer minecraftServer) {
    Squaremap api = SquaremapProvider.get();

    api.mapWorlds().forEach(mapWorld -> mapWorld.layerRegistry().register(WAYSTONES_LAYER, provider));

    registerWaystoneIcon(api);

    if (FabricWaystones.WAYSTONE_STORAGE != null) {
      FabricWaystones.WAYSTONE_STORAGE.getAllHashes().forEach(this::addWaypoint);
    } else {
      LOGGER.error("WAYSTONE_STORAGE null");
    }
  }

  private void addWaypoint(String hash) {
    Key hashKey = hashToKey(hash);

    if (FabricWaystones.WAYSTONE_STORAGE == null || provider.hasMarker(hashKey)) {
      LOGGER.error("Failed adding waystone: WAYSTONE_STORAGE is null (or waystone already exists)");
      return; // do not recreate waypoint
    }

    WaystoneValue waystone = FabricWaystones.WAYSTONE_STORAGE.getWaystoneData(hash);
    if (waystone == null) {
      LOGGER.error("Failed adding waystone: Waystone data is null");
      return;
    }

    String waystoneName = waystone.getWaystoneName();
    MarkerOptions waystoneOptions = MarkerOptions.builder()
                                                 .hoverTooltip(waystoneName)
                                                 .fillOpacity(1.0).strokeOpacity(1.0)
                                                 .build();

    Point waystonePosition = Point.of(waystone.way_getPos().getX(), waystone.way_getPos().getZ());
    Marker waystoneMarker = Marker.icon(waystonePosition, WAYSTONES_ICON_KEY, WAYSTONES_ICON_SIZE)
                                  .markerOptions(waystoneOptions);

    provider.addMarker(hashKey, waystoneMarker);
  }

  private static void registerWaystoneIcon(Squaremap api) {
    URL imageUrl = FabricWaystones.class.getResource("/fabric_waystones_icon.png");

    assert imageUrl != null;

    final BufferedImage image;
    try {
      image = ImageIO.read(imageUrl);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    api.iconRegistry().register(WAYSTONES_ICON_KEY, image);
  }

  private Key hashToKey(String hash) {
    return Key.of(String.valueOf(hash.hashCode()));
  }
}

package com.kalaazu.server.service;

import com.artemis.World;
import com.artemis.utils.IntBag;
import com.kalaazu.KalaazuConfig;
import com.kalaazu.math.Vector;
import com.kalaazu.persistence.entity.MapsEntity;
import com.kalaazu.persistence.service.MapsService;
import com.kalaazu.server.ecs.component.EntityTypeComponent;
import com.kalaazu.server.ecs.component.map.PositionComponent;
import com.kalaazu.server.event.SendCommands;
import com.kalaazu.server.game.commands.CommandBuilder;
import com.kalaazu.server.game.commands.CommandType;
import com.kalaazu.server.game.commands.OutCommand;
import com.kalaazu.server.game.netty.GameSession;
import com.kalaazu.server.game.util.ServerCommands;
import com.kalaazu.server.game.v4.commands.out.settings.ClientSettingsCommand;
import com.kalaazu.util.Logger;
import com.kalaazu.util.LoggingCategory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Map Service.
 * ==========
 *
 * This service is responsible for managing the state of all game maps,
 * including the entities within them (NPCs, players, portals, etc.).
 * It handles the initial loading of map data from the database and the
 * process of initializing a player when they join a map.
 *
 * @example
 * ```java
 * // This service is managed by Spring and should be injected.
 * // @Service
 * // public class GameInitializationService {
 * //
 * //     @Autowired
 * //     private MapService mapService;
 * //
 * //     public void initializePlayer(GameSession session) {
 * //         mapService.initializePlayer(session);
 * //     }
 * // }
 * ```
 *
 * @see com.kalaazu.server.service.GameLoopService
 * @see com.kalaazu.server.game.netty.GameSession
 *
 * @author manulaiko
 */
@Service
@RequiredArgsConstructor
public class MapService implements Logger {
    @Getter
    private final LoggingCategory category = LoggingCategory.MAP;

    private final ApplicationContext ctx;
    private final TaskScheduler taskScheduler;
    private final KalaazuConfig config;
    private final MapsService service;
    private final GameLoopService gameLoopService;
    private final CommandBuilder commandBuilder;

    private Map<Short, MapsEntity> maps;
    private boolean isInitialized = false;

    /**
     * Initializes the map service by loading all map configurations from the database
     * and populating each map with its static entities (NPCs, portals, etc.).
     * This method is idempotent and will only execute its logic once, even if called
     * multiple times. It should typically be invoked at server startup.
     *
     * @example
     * ```java
     * // In a component that runs on application startup.
     * // @Component
     * // public class ServerInitializer {
     * //
     * //     @Autowired
     * //     private MapService mapService;
     * //
     * //     @PostConstruct
     * //     public void onStartup() {
     * //         mapService.initialize();
     * //     }
     * // }
     * ```
     */
    public void initialize() {
        if (isInitialized) {
            return;
        }
        isInitialized = true;

        info("Loading maps..");
        maps = service.findAll()
                .stream()
                .filter(m -> m.getId() == 1)
                .collect(Collectors.toMap(MapsEntity::getId, (v) -> v));

        maps.forEach(this::initializeMap);
    }

    /**
     * Initializes a single game map by setting up its game loop and populating it
     * with all configured static entities like NPCs, collectables, stations, and portals.
     * It generates unique IDs for each entity and places them on the map according
     * to the database configuration.
     *
     * @param mapId The unique identifier of the map to initialize.
     * @param map The database entity containing the configuration for the map.
     *
     * @example
     * ```java
     * // This is a private method called by initialize().
     * // MapsEntity spaceMap = mapsRepository.findById(1);
     * // initializeMap(spaceMap.getId(), spaceMap);
     * ```
     */
    private void initializeMap(Short mapId, MapsEntity map) {
        info("Initializing map {}", map.getName());

        gameLoopService.initializeMap(map);

        var stationIdGenerator = new AtomicInteger();
        var portalIdGenerator = new AtomicInteger();

        var npcIds = new ArrayList<Integer>();
        map.getMapsNpcs()
                .forEach(npc -> {
                    for (int i = 0; i < Math.max(0, npc.getAmount()); i++) {
                        var position = Vector.random(map.getLimits().margin());

                        int id;
                        do {
                            id = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, 0);
                        } while (npcIds.contains(id));

                        gameLoopService.addNpc(map, npc.getNpcsByNpcsId(), position, id);

                        npcIds.add(id);
                    }
                });

        var collectablesIds = new ArrayList<Integer>();
        map.getMapsCollectables()
                .forEach(collectable -> {
                    for (int i = 0; i < Math.max(0, collectable.getAmount()); i++) {
                        var position = Vector.random(collectable.getRegion());

                        int id;
                        do {
                            id = ThreadLocalRandom.current().nextInt();
                        } while (collectablesIds.contains(id));

                        gameLoopService.addCollectable(mapId, collectable.getCollectablesByCollectablesId(), position, id);

                        collectablesIds.add(id);
                    }
                });

        map.getMapsStations()
                .forEach(station -> gameLoopService.addStation(mapId, station, stationIdGenerator.getAndIncrement()));

        map.getMapsPortals()
                .forEach(portal -> gameLoopService.addPortal(mapId, portal, portalIdGenerator.getAndIncrement()));

        info(
                "Initialized map {} with {} npcs, {} collectables, {} stations and {} portals",
                map.getName(),
                npcIds.size(),
                collectablesIds.size(),
                map.getMapsStations().size(), map.getMapsPortals().size()
        );
    }

    /**
     * Handles the process of a player entering a map. This includes creating the
     * player's entity in the corresponding map's ECS world, setting the entity ID
     * in their session, and sending them the necessary commands to render the map
     * and all nearby entities (players, NPCs, collectables) as well as static
     * entities like portals and stations.
     *
     * @param session The `GameSession` of the player who is entering the map.
     *
     * @example
     * ```java
     * // GameSession playerSession = getSessionForAuthenticatedPlayer();
     * // mapService.initializePlayer(playerSession);
     * ```
     */
    public void initializePlayer(GameSession session) {
        var account = session.getAccount();
        var ship = session.getShip();
        var config = session.getConfiguration();
        var map = session.getMapId();

        if (!maps.containsKey(map)) {
            info("Invalid map {}!", map);

            return;
        }

        info("Initializing player {}", account.getId());

        var player = gameLoopService.addPlayer(
                maps.get(map),
                session,
                account,
                ship,
                config
        );

        session.setEntityId(player);

        var world = gameLoopService.getEngine(map);
        var commands = new ArrayList<OutCommand>();
        var entityTypeMapper = world.getMapper(EntityTypeComponent.class);

        // Send only nearby entities to the player
        var playersInRadius = findPlayersInRadius(world, map, ship.getPosition());
        for (int i = 0, s = playersInRadius.size(); i < s; i++) {
            var entityId = playersInRadius.get(i);
            var entityType = entityTypeMapper.get(entityId);
            commands.addAll(commandBuilder.buildCommands(CommandType.EntityCreationCommand, world, entityId, entityType.getType()));
        }

        var npcsInRadius = findNpcsInRadius(world, map, ship.getPosition());
        for (int i = 0, s = npcsInRadius.size(); i < s; i++) {
            var entityId = npcsInRadius.get(i);
            var entityType = entityTypeMapper.get(entityId);
            commands.addAll(commandBuilder.buildCommands(CommandType.EntityCreationCommand, world, entityId, entityType.getType()));
        }

        var collectablesInRadius = findCollectablesInRadius(world, map, ship.getPosition());
        for (int i = 0, s = collectablesInRadius.size(); i < s; i++) {
            var entityId = collectablesInRadius.get(i);
            var entityType = entityTypeMapper.get(entityId);
            commands.addAll(commandBuilder.buildCommands(CommandType.EntityCreationCommand, world, entityId, entityType.getType()));
        }

        // Stations and portals are always sent
        var stations = getStations(map);
        for (int i = 0, s = stations.size(); i < s; i++) {
            var entityId = stations.get(i);
            var entityType = entityTypeMapper.get(entityId);
            commands.addAll(commandBuilder.buildCommands(CommandType.EntityCreationCommand, world, entityId, entityType.getType()));
        }

        var portals = getPortals(map);
        for (int i = 0, s = portals.size(); i < s; i++) {
            var entityId = portals.get(i);
            var entityType = entityTypeMapper.get(entityId);
            commands.addAll(commandBuilder.buildCommands(CommandType.EntityCreationCommand, world, entityId, entityType.getType()));
        }

        commands.add(new ClientSettingsCommand(ServerCommands.MAP_READY_HANDSHAKE, 0));

        ctx.publishEvent(new SendCommands(session, commands));
    }

    /**
     * Finds all player entities within the configured render distance of a central point.
     *
     * @param world  The ECS `World` instance for the map.
     * @param mapId  The ID of the map to search in.
     * @param center The `Vector` representing the center of the search area.
     *
     * @return An `IntBag` containing the entity IDs of all players found within the radius.
     *
     * @see #findInRadius(World, IntBag, Vector, double)
     */
    private IntBag findPlayersInRadius(World world, short mapId, Vector center) {
        return findInRadius(world, gameLoopService.getPlayers(mapId), center, config.getGame().getRenderDistance());
    }

    private IntBag findNpcsInRadius(World world, short mapId, Vector center) {
        return findInRadius(world, gameLoopService.getNpcs(mapId), center, config.getGame().getRenderDistance());
    }

    private IntBag findCollectablesInRadius(World world, short mapId, Vector center) {
        return findInRadius(world, gameLoopService.getCollectables(mapId), center, config.getGame().getRenderDistance());
    }

    /**
     * Retrieves the entity IDs of all stations on a given map.
     *
     * @param mapId The ID of the map.
     *
     * @return An `IntBag` containing the entity IDs of all stations on the specified map.
     *
     * @example
     * ```java
     * short mapId = 1;
     * IntBag stations = mapService.getStations(mapId);
     * ```
     */
    public IntBag getStations(short mapId) {
        return gameLoopService.getStations(mapId);
    }

    /**
     * Retrieves the entity IDs of all portals on a given map.
     *
     * @param mapId The ID of the map.
     *
     * @return An `IntBag` containing the entity IDs of all portals on the specified map.
     *
     * @example
     * ```java
     * short mapId = 1;
     * IntBag portals = mapService.getPortals(mapId);
     * ```
     */
    public IntBag getPortals(short mapId) {
        return gameLoopService.getPortals(mapId);
    }

    /**
     * Filters a given list of entities, returning only those within a specified radius
     * of a central point. This method performs an efficient distance check by comparing
     * squared distances to avoid costly square root calculations.
     *
     * @param world The ECS `World` instance used to access entity components.
     * @param entities An `IntBag` of entity IDs to filter.
     * @param center The `Vector` representing the center of the search radius.
     * @param radius The search radius.
     *
     * @return A new `IntBag` containing only the entity IDs that are within the specified radius.
     *
     * @example
     * ```java
     * World world = gameLoopService.getEngine(mapId);
     * IntBag allNpcs = gameLoopService.getNpcs(mapId);
     * Vector searchCenter = new Vector(10000, 10000);
     * double searchRadius = 2000;
     *
     * IntBag npcsInRadius = findInRadius(world, allNpcs, searchCenter, searchRadius);
     * ```
     */
    private IntBag findInRadius(World world, IntBag entities, Vector center, double radius) {
        if (entities == null || entities.isEmpty()) {
            return new IntBag();
        }

        // Use squared distance to avoid expensive square root operations
        double radiusSq = radius * radius;

        var positionMapper = world.getMapper(PositionComponent.class);

        var result = new IntBag();
        for (int i = 0, s = entities.size(); i < s; i++) {
            var entityId = entities.get(i);
            var position = positionMapper.get(entityId);
            if (position != null && position.getPosition().dst2(center) <= radiusSq) {
                result.add(entityId);
            }
        }

        return result;
    }

    /**
     * Retrieves the entity IDs of all players on a given map.
     *
     * @param mapId The ID of the map.
     *
     * @return An `IntBag` containing the entity IDs of all players on the specified map.
     *
     * @example ```java
     * short mapId = 1;
     * IntBag players = mapService.getPlayers(mapId);
     * ```
     */
    public IntBag getPlayers(short mapId) {
        return gameLoopService.getPlayers(mapId);
    }

    /**
     * Retrieves the entity IDs of all NPCs on a given map.
     *
     * @param mapId The ID of the map.
     *
     * @return An `IntBag` containing the entity IDs of all NPCs on the specified map.
     *
     * @example ```java
     * short mapId = 1;
     * IntBag npcs = mapService.getNpcs(mapId);
     * ```
     */
    public IntBag getNpcs(short mapId) {
        return gameLoopService.getNpcs(mapId);
    }

    /**
     * Retrieves the entity IDs of all collectables on a given map.
     *
     * @param mapId The ID of the map.
     *
     * @return An `IntBag` containing the entity IDs of all collectables on the specified map.
     *
     * @example ```java
     * short mapId = 1;
     * IntBag collectables = mapService.getCollectables(mapId);
     * ```
     */
    public IntBag getCollectables(short mapId) {
        return gameLoopService.getCollectables(mapId);
    }
}

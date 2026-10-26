package com.dex.boxcasino.extra;

import com.dex.boxcasino.BoxCasinoPlugin;
import com.dex.boxcasino.config.ItemSpec;
import com.dex.boxcasino.config.Messages;
import com.dex.boxcasino.game.Game;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Casino machines (spec 14): an {@link ItemDisplay} with the machine model, {@link Interaction} boxes covering the
 * whole model (a wide table gets several), optionally barrier blocks under it so players cannot walk through, and the
 * chairs / stools of {@code machines.seats.layouts} (chair model + click box + invisible seat to ride, see
 * {@link SeatService}). Every part is persistent and tagged with the machine, so machines survive restarts without a
 * separate file. Right-clicking a chair sits there; right-clicking the model sits on a random free chair (machines
 * without chairs open the game directly).
 * <p>
 * Where the model really is comes from {@code machines.bounds} in config.yml: the model box relative to the display
 * entity (HEAD transform of the pack model, scale 1, before turning), measured from the pack files.
 */
public final class MachineService implements Listener {

    /** Box of a model in the world: x/z footprint (already turned) and height. */
    private record Box(double x0, double z0, double x1, double z1, double y0, double y1) {
    }

    private static final double[] DEFAULT_BOUNDS = {-0.5, -2.7, -0.5, 0.5, -1.1, 0.5};

    private final BoxCasinoPlugin plugin;
    private final NamespacedKey gameKey;
    private final NamespacedKey displayKey;
    private final NamespacedKey barriersKey;
    /** Chair number on the chair model, its click box and its seat. */
    private final NamespacedKey seatKey;
    /** "chair" / "seat" on the extra displays of a machine. */
    private final NamespacedKey partKey;
    /** Number of a machine among the machines of its game ("Máy 2", "Bàn 2"). */
    private final NamespacedKey numberKey;
    /** Next free number per game, kept in machines.yml. */
    private org.bukkit.configuration.file.YamlConfiguration numbers;

    public MachineService(BoxCasinoPlugin plugin) {
        this.plugin = plugin;
        this.gameKey = new NamespacedKey(plugin, "machine");
        this.displayKey = new NamespacedKey(plugin, "machine_display");
        this.barriersKey = new NamespacedKey(plugin, "machine_barriers");
        this.seatKey = new NamespacedKey(plugin, "machine_seat");
        this.partKey = new NamespacedKey(plugin, "machine_part");
        this.numberKey = new NamespacedKey(plugin, "machine_number");
    }

    private ConfigurationSection config() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("machines");
        return c == null ? plugin.getConfig().createSection("machines") : c;
    }

    private double[] bounds(String game) {
        List<Double> l = plugin.getConfig().getDoubleList("machines.bounds." + game);
        if (l.size() != 6) return DEFAULT_BOUNDS;
        double[] b = new double[6];
        for (int i = 0; i < 6; i++) b[i] = l.get(i);
        return b;
    }

    /** Places a machine standing on the block the admin looks at, facing the admin. */
    public void place(Player admin, Game game) {
        Messages m = plugin.messages();
        Object look = config().get("items." + game.id());
        if (look == null) {
            m.send(admin, "machine.no-model", "game", game.id());
            return;
        }
        Block target = admin.getTargetBlockExact(6);
        if (target == null) {
            m.send(admin, "machine.no-target");
            return;
        }
        Block spot = target.getRelative(BlockFace.UP);
        if (!spot.getType().isAir()) {
            m.send(admin, "machine.blocked");
            return;
        }
        // face the admin, snapped to 90 degrees
        float yaw = Math.round((admin.getLocation().getYaw() + 180f) / 90f) * 90f;
        float s = (float) config().getDouble("scale", 1.0);
        // the model hangs below the display entity (HEAD transform): lift it so its feet stand on the block
        double lift = -s * bounds(game.id())[1] + config().getDouble("y-adjust", 0);
        Location at = spot.getLocation().add(0.5, lift, 0.5);
        at.setYaw(yaw);

        ItemDisplay display = spot.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(plugin.items().make(ItemSpec.of(look, ItemSpec.NONE), Messages.itemLine(" "), List.of()));
            ItemDisplay.ItemDisplayTransform transform;
            try {
                transform = ItemDisplay.ItemDisplayTransform.valueOf(config().getString("display", "HEAD"));
            } catch (IllegalArgumentException ex) {
                transform = ItemDisplay.ItemDisplayTransform.HEAD;
            }
            d.setItemDisplayTransform(transform);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
            d.setRotation(yaw, 0);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(gameKey, PersistentDataType.STRING, game.id());
            d.getPersistentDataContainer().set(numberKey, PersistentDataType.INTEGER, nextNumber(game.id()));
        });
        build(display, game.id());
        m.send(admin, "machine.placed", "game", game.displayName());
    }

    /** The model box of {@code display} in the world. */
    private Box box(ItemDisplay display, String game) {
        double[] b = bounds(game);
        double s = display.getTransformation().getScale().y();
        Location o = display.getLocation();
        // display turn (-yaw) plus the half turn every item display applies to its item
        double a = Math.PI - Math.toRadians(o.getYaw());
        double cos = Math.cos(a), sin = Math.sin(a);
        double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (double x : new double[] {b[0], b[3]}) {
            for (double z : new double[] {b[2], b[5]}) {
                double wx = s * (x * cos + z * sin);
                double wz = s * (-x * sin + z * cos);
                x0 = Math.min(x0, wx);
                x1 = Math.max(x1, wx);
                z0 = Math.min(z0, wz);
                z1 = Math.max(z1, wz);
            }
        }
        return new Box(o.getX() + x0, o.getZ() + z0, o.getX() + x1, o.getZ() + z1, o.getY() + s * b[1], o.getY() + s * b[4]);
    }

    /** Click boxes over the whole model and barriers at its feet. */
    private void build(ItemDisplay display, String game) {
        Box box = box(display, game);
        World world = display.getWorld();
        double w = box.x1() - box.x0(), d = box.z1() - box.z0(), h = box.y1() - box.y0();
        // interaction boxes are square: a wide model gets a row of them
        int nx = Math.max(1, (int) Math.ceil(w - 0.05));
        int nz = Math.max(1, (int) Math.ceil(d - 0.05));
        float cell = (float) (Math.max(w / nx, d / nz) + 0.04);
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) {
                Location at = new Location(world, box.x0() + (i + 0.5) * w / nx, box.y0(), box.z0() + (j + 0.5) * d / nz);
                world.spawn(at, Interaction.class, it -> {
                    it.setInteractionWidth(cell);
                    it.setInteractionHeight((float) h);
                    it.setResponsive(true);
                    it.setPersistent(true);
                    it.getPersistentDataContainer().set(gameKey, PersistentDataType.STRING, game);
                    it.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, display.getUniqueId().toString());
                });
            }
        }
        // the (possibly empty) barrier list also marks the machine as built by this version
        display.getPersistentDataContainer().set(barriersKey, PersistentDataType.STRING, "");
        if (config().getBoolean("place-barrier", true)) placeBarriers(display, box);
        buildSeats(display, game, box);
        buildHologram(display, game, box);
        buildDealer(display, game, box);
    }

    // --- hologram: game name + free chairs, above the model ---

    private void buildHologram(ItemDisplay display, String game, Box box) {
        ConfigurationSection c = config().getConfigurationSection("hologram");
        if (c != null && !c.getBoolean("enabled", true)) return;
        double height = c == null ? 0.5 : c.getDouble("height", 0.5);
        float scale = (float) (c == null ? 1.0 : c.getDouble("scale", 1.0));
        String machine = display.getUniqueId().toString();
        Location at = new Location(display.getWorld(), (box.x0() + box.x1()) / 2, box.y1() + height, (box.z0() + box.z1()) / 2);
        display.getWorld().spawn(at, org.bukkit.entity.TextDisplay.class, t -> {
            t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            t.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
            t.setShadowed(c == null || c.getBoolean("shadow", true));
            String bg = c == null ? "" : c.getString("background", "");
            if (bg != null && bg.matches("#?[0-9a-fA-F]{8}")) {
                t.setBackgroundColor(org.bukkit.Color.fromARGB((int) Long.parseLong(bg.replace("#", ""), 16)));
            }
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale),
                    new AxisAngle4f()));
            t.setPersistent(true);
            PersistentDataContainer pdc = t.getPersistentDataContainer();
            pdc.set(displayKey, PersistentDataType.STRING, machine);
            pdc.set(gameKey, PersistentDataType.STRING, game);
            pdc.set(partKey, PersistentDataType.STRING, "holo");
            t.text(holoText(game, machine));
        });
    }

    // --- numbers ---

    private org.bukkit.configuration.file.YamlConfiguration numbers() {
        if (numbers == null) {
            numbers = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.File(plugin.getDataFolder(), "machines.yml"));
        }
        return numbers;
    }

    private int nextNumber(String game) {
        int n = numbers().getInt("next." + game, 1);
        numbers().set("next." + game, n + 1);
        try {
            numbers().save(new java.io.File(plugin.getDataFolder(), "machines.yml"));
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Could not save machines.yml: " + ex.getMessage());
        }
        return n;
    }

    /** Highest number given to a machine of {@code game} (0 = none placed yet). */
    public int highestNumber(String game) {
        return numbers().getInt("next." + game, 1) - 1;
    }

    /** Number of a machine ("Máy 3" / "Bàn 3"), 0 when the machine is not loaded. */
    public int number(String machine) {
        try {
            Entity e = Bukkit.getEntity(UUID.fromString(machine));
            Integer n = e == null ? null : e.getPersistentDataContainer().get(numberKey, PersistentDataType.INTEGER);
            return n == null ? 0 : n;
        } catch (IllegalArgumentException ex) {
            return 0;
        }
    }

    /** Games played by several players at one table: "Bàn X" instead of "Máy X". */
    public boolean tableGame(String game) {
        return plugin.getConfig().getStringList("machines.table-games").contains(game);
    }

    /** "2/3" with the free count green while a chair is free, red when full. */
    public String freeText(int free, int total) {
        Messages m = plugin.messages();
        return m.raw("machine.holo.free", "free",
                m.raw(free > 0 ? "machine.holo.free-color" : "machine.holo.full-color") + free, "total", String.valueOf(total));
    }

    /** "DÒ MÌN - Máy 1" in the game colour, then "Ghế trống: 1/3" (green while a chair is free, red when full). */
    private net.kyori.adventure.text.Component holoText(String game, String machine) {
        Messages m = plugin.messages();
        String name = m.raw("games." + game + ".name").toUpperCase(java.util.Locale.ROOT);
        String title = m.raw(tableGame(game) ? "machine.holo.title-table" : "machine.holo.title-machine",
                "name", name, "number", String.valueOf(number(machine)));
        int total = seatCount(game);
        String second;
        if (total == 0) {
            second = m.raw("machine.holo.no-seats");
        } else {
            int free = plugin.seats().freeSeats(machine, total);
            second = m.raw("machine.holo.seats", "seats", freeText(free, total));
        }
        // a text display has no padding: spaces around each line widen the background, empty lines make it taller
        ConfigurationSection c = config().getConfigurationSection("hologram");
        String side = " ".repeat(Math.max(0, Math.min(20, c == null ? 3 : c.getInt("padding", 3))));
        String blank = "\n".repeat(Math.max(0, Math.min(3, c == null ? 0 : c.getInt("padding-lines", 0))));
        String body = side + title + side + (second.isEmpty() ? "" : "\n" + side + second + side);
        return Messages.parse(blank.isEmpty() ? body : " " + blank + body + blank + " ");
    }

    /** Redraws the hologram of a machine (someone sat down / stood up). */
    void updateHologram(String machine, Location near) {
        for (Entity e : near.getWorld().getNearbyEntities(near, 8, 8, 8)) {
            if (e instanceof org.bukkit.entity.TextDisplay t && isHolo(t, machine)) {
                t.text(holoText(t.getPersistentDataContainer().get(gameKey, PersistentDataType.STRING), machine));
            }
        }
    }

    private boolean isHolo(Entity e, String machine) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        return "holo".equals(pdc.get(partKey, PersistentDataType.STRING))
                && (machine == null || machine.equals(pdc.get(displayKey, PersistentDataType.STRING)));
    }

    /** Holograms loaded from disk still show the chairs of before the restart: nobody sits now. */
    @EventHandler
    public void onEntitiesLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) refreshHolo(en);
    }

    /** Plugin start / reload: every loaded hologram shows the current free chairs and texts. */
    public void refreshHolograms() {
        for (World w : Bukkit.getWorlds()) for (Entity en : w.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)) refreshHolo(en);
    }

    private void refreshHolo(Entity en) {
        if (!(en instanceof org.bukkit.entity.TextDisplay t) || !isHolo(t, null)) return;
        String game = t.getPersistentDataContainer().get(gameKey, PersistentDataType.STRING);
        String machine = t.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING);
        if (game != null && machine != null) t.text(holoText(game, machine));
    }

    // --- dealer NPC (ZNPCsPlus) ---

    private void buildDealer(ItemDisplay display, String game, Box box) {
        String spot = plugin.dealers().spot(game);
        if (spot == null) return;
        String[] p = spot.trim().split("\\s+");
        double distance = 0.4;
        try {
            if (p.length >= 2) distance = Double.parseDouble(p[1]);
        } catch (NumberFormatException ignored) {
        }
        float yaw = display.getLocation().getYaw();
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        double rx = -fz, rz = fx;
        double cx = (box.x0() + box.x1()) / 2, cz = (box.z0() + box.z1()) / 2;
        boolean alongX = Math.abs(fx) > 0.5;
        double halfDepth = (alongX ? box.x1() - box.x0() : box.z1() - box.z0()) / 2;
        double halfWidth = (alongX ? box.z1() - box.z0() : box.x1() - box.x0()) / 2;
        double x, z;
        float facing;
        switch (p[0].toLowerCase(java.util.Locale.ROOT)) {
            case "left" -> {
                x = cx - rx * (halfWidth + distance);
                z = cz - rz * (halfWidth + distance);
                facing = yaw - 90f;
            }
            case "right" -> {
                x = cx + rx * (halfWidth + distance);
                z = cz + rz * (halfWidth + distance);
                facing = yaw + 90f;
            }
            default -> {
                // behind the table, facing the players on the front chairs
                x = cx - fx * (halfDepth + distance);
                z = cz - fz * (halfDepth + distance);
                facing = yaw;
            }
        }
        plugin.dealers().spawn(display.getUniqueId().toString(), new Location(display.getWorld(), x, box.y0(), z, facing, 0));
    }

    private void placeBarriers(ItemDisplay display, Box box) {
        World world = display.getWorld();
        double h = box.y1() - box.y0();
        // barriers on every block column whose centre is under the model; 1 layer for low tables, 2 for tall machines
        int layers = Math.max(1, Math.min(2, (int) Math.floor(h + 0.25)));
        int floorY = (int) Math.floor(box.y0() + 0.01);
        List<String> placed = new ArrayList<>();
        for (int bx = (int) Math.floor(box.x0()); bx <= (int) Math.floor(box.x1()); bx++) {
            for (int bz = (int) Math.floor(box.z0()); bz <= (int) Math.floor(box.z1()); bz++) {
                if (bx + 0.5 < box.x0() || bx + 0.5 > box.x1() || bz + 0.5 < box.z0() || bz + 0.5 > box.z1()) continue;
                for (int l = 0; l < layers; l++) {
                    Block block = world.getBlockAt(bx, floorY + l, bz);
                    if (!block.getType().isAir()) continue;
                    block.setType(Material.BARRIER);
                    placed.add(bx + "," + (floorY + l) + "," + bz);
                }
            }
        }
        display.getPersistentDataContainer().set(barriersKey, PersistentDataType.STRING, String.join(";", placed));
    }

    // --- chairs ---

    /** One chair of a layout: model (chair / stool), side of the machine (front / back) and sideways offset. */
    private record SeatSpot(String model, int side, double lateral) {
    }

    private List<SeatSpot> layout(String game) {
        List<SeatSpot> out = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("machines.seats.layouts." + game)) {
            String[] p = line.trim().split("\\s+");
            if (p.length < 2) continue;
            double lateral = 0;
            try {
                if (p.length >= 3) lateral = Double.parseDouble(p[2]);
            } catch (NumberFormatException ignored) {
            }
            out.add(new SeatSpot(p[0], p[1].equalsIgnoreCase("back") ? -1 : 1, lateral));
        }
        return out;
    }

    /** Number of chairs of a machine of {@code game} (0 = no chairs, the model opens the game). */
    public int seatCount(String game) {
        return layout(game).size();
    }

    /**
     * Chairs around the model. Front = the side that faced the admin when placing; a chair stands {@code gap} blocks out
     * from that side of the model box and faces the model.
     */
    private void buildSeats(ItemDisplay display, String game, Box box) {
        List<SeatSpot> spots = layout(game);
        if (spots.isEmpty()) return;
        ConfigurationSection c = config().getConfigurationSection("seats");
        double gap = c == null ? 0.55 : c.getDouble("gap", 0.55);
        double sit = c == null ? 0.55 : c.getDouble("sit-height", 0.55);
        float scale = (float) (c == null ? 1.0 : c.getDouble("scale", 1.0));
        float yawOffset = (float) (c == null ? 0 : c.getDouble("yaw-offset", 0));
        World world = display.getWorld();
        float yaw = display.getLocation().getYaw();
        // forward = towards the admin who placed it, right = forward turned clockwise
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        double rx = -fz, rz = fx;
        double cx = (box.x0() + box.x1()) / 2, cz = (box.z0() + box.z1()) / 2;
        boolean alongX = Math.abs(fx) > 0.5;
        double halfDepth = (alongX ? box.x1() - box.x0() : box.z1() - box.z0()) / 2;
        String id = display.getUniqueId().toString();
        for (int i = 0; i < spots.size(); i++) {
            SeatSpot spot = spots.get(i);
            double out = spot.side() * (halfDepth + gap);
            double x = cx + fx * out + rx * spot.lateral(), z = cz + fz * out + rz * spot.lateral();
            // the chair (and whoever sits on it) faces the model
            float chairYaw = (spot.side() > 0 ? yaw + 180f : yaw) + yawOffset;
            int index = i;
            Object look = c == null ? null : c.get(spot.model());
            world.spawn(new Location(world, x, box.y0() + 0.547 * scale, z, chairYaw, 0), ItemDisplay.class, d -> {
                d.setItemStack(plugin.items().make(ItemSpec.of(look, new ItemSpec("", Material.OAK_STAIRS, 0)),
                        Messages.itemLine(" "), List.of()));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(scale, scale, scale), new AxisAngle4f()));
                d.setRotation(chairYaw, 0);
                d.setPersistent(true);
                tagPart(d.getPersistentDataContainer(), id, index, "chair");
            });
            world.spawn(new Location(world, x, box.y0() + sit, z, chairYaw, 0), ItemDisplay.class, d -> {
                d.setRotation(chairYaw, 0);
                d.setPersistent(true);
                tagPart(d.getPersistentDataContainer(), id, index, "seat");
            });
            world.spawn(new Location(world, x, box.y0(), z), Interaction.class, it -> {
                it.setInteractionWidth(0.9f * scale);
                it.setInteractionHeight(1.15f * scale);
                it.setResponsive(true);
                it.setPersistent(true);
                PersistentDataContainer pdc = it.getPersistentDataContainer();
                pdc.set(gameKey, PersistentDataType.STRING, game);
                pdc.set(displayKey, PersistentDataType.STRING, id);
                pdc.set(seatKey, PersistentDataType.INTEGER, index);
            });
        }
    }

    private void tagPart(PersistentDataContainer pdc, String machine, int index, String part) {
        pdc.set(displayKey, PersistentDataType.STRING, machine);
        pdc.set(seatKey, PersistentDataType.INTEGER, index);
        pdc.set(partKey, PersistentDataType.STRING, part);
    }

    /** The seat entity (to ride) of chair {@code index} of a machine near {@code near}, or null. */
    Entity seatEntity(Location near, String machine, int index) {
        for (Entity e : near.getWorld().getNearbyEntities(near, 8, 8, 8)) {
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            if ("seat".equals(pdc.get(partKey, PersistentDataType.STRING))
                    && machine.equals(pdc.get(displayKey, PersistentDataType.STRING))
                    && Integer.valueOf(index).equals(pdc.get(seatKey, PersistentDataType.INTEGER))) {
                return e;
            }
        }
        return null;
    }

    /** Removes the click boxes, chairs and barriers of {@code display} (also the single box + barrier of older versions). */
    private void unbuild(ItemDisplay display) {
        String id = display.getUniqueId().toString();
        plugin.dealers().remove(id);
        boolean legacy = !display.getPersistentDataContainer().has(barriersKey, PersistentDataType.STRING);
        for (Entity e : display.getNearbyEntities(8, 8, 8)) {
            if (e == display || !id.equals(e.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING))) continue;
            if (legacy && e instanceof Interaction && !e.getPersistentDataContainer().has(seatKey, PersistentDataType.INTEGER)) {
                clearBarrier(e.getLocation().getBlock());
            }
            e.eject();
            e.remove();
        }
        String barriers = display.getPersistentDataContainer().get(barriersKey, PersistentDataType.STRING);
        if (barriers != null && !barriers.isEmpty()) {
            for (String p : barriers.split(";")) {
                String[] xyz = p.split(",");
                clearBarrier(display.getWorld().getBlockAt(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
            }
        }
        display.getPersistentDataContainer().remove(barriersKey);
    }

    private static void clearBarrier(Block block) {
        if (block.getType() == Material.BARRIER) block.setType(Material.AIR);
    }

    /** Removes the machine the admin looks at. */
    public void remove(Player admin) {
        RayTraceResult hit = admin.getWorld().rayTraceEntities(admin.getEyeLocation(), admin.getEyeLocation().getDirection(), 6,
                0.2, e -> e instanceof Interaction && e.getPersistentDataContainer().has(gameKey, PersistentDataType.STRING));
        if (hit == null || hit.getHitEntity() == null) {
            plugin.messages().send(admin, "machine.not-found");
            return;
        }
        Entity interaction = hit.getHitEntity();
        String displayId = interaction.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING);
        Entity display = displayId == null ? null : admin.getServer().getEntity(UUID.fromString(displayId));
        if (display instanceof ItemDisplay d) {
            unbuild(d);
            d.remove();
        } else {
            clearBarrier(interaction.getLocation().getBlock());
            interaction.remove();
        }
        plugin.messages().send(admin, "machine.removed");
    }

    /** Rebuilds click boxes and barriers of every machine near the admin; the models stay where they are. */
    public void refit(Player admin, double radius) {
        int count = 0;
        for (Entity e : admin.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof ItemDisplay d)) continue;
            String game = d.getPersistentDataContainer().get(gameKey, PersistentDataType.STRING);
            if (game == null) continue;
            unbuild(d);
            if (!d.getPersistentDataContainer().has(numberKey, PersistentDataType.INTEGER)) {
                d.getPersistentDataContainer().set(numberKey, PersistentDataType.INTEGER, nextNumber(game));
            }
            build(d, game);
            count++;
        }
        plugin.messages().send(admin, count == 0 ? "machine.refit-none" : "machine.refit", "count", String.valueOf(count),
                "radius", String.valueOf((int) radius));
    }

    // --- menu: go to a machine ---

    /** The model displays of every placed machine of {@code game} in the loaded chunks. */
    private List<ItemDisplay> machinesOf(String game) {
        List<ItemDisplay> out = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class)) {
                if (game.equals(d.getPersistentDataContainer().get(gameKey, PersistentDataType.STRING))) out.add(d);
            }
        }
        return out;
    }

    /**
     * Menu click on a game: the nearest machine of it with a free chair, the player is teleported onto that chair
     * (which opens the game). Machines without chairs (shared tables) take everyone: the player lands in front of the
     * nearest one and the game opens. Tells the player when no machine has room or none is placed.
     */
    public void goTo(Player player, Game game) {
        Messages m = plugin.messages();
        SeatService.Seated seated = plugin.seats().of(player.getUniqueId());
        if (seated != null && seated.game().equals(game.id())) {
            // already on a chair of this game: just open it again
            plugin.seats().sitAnywhere(player, seated.machine(), game.id(), player.getLocation());
            return;
        }
        List<ItemDisplay> machines = machinesOf(game.id());
        if (machines.isEmpty()) {
            m.send(player, "menu.no-machine", "game", game.displayName());
            return;
        }
        Location from = player.getLocation();
        machines.sort(java.util.Comparator.comparingDouble(d -> d.getWorld().equals(from.getWorld())
                ? d.getLocation().distanceSquared(from) : Double.MAX_VALUE));
        int seats = seatCount(game.id());
        if (seats == 0) {
            ItemDisplay d = machines.get(0);
            player.teleport(front(d, game.id()));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) open(player, game.id());
            });
            return;
        }
        for (ItemDisplay d : machines) {
            String id = d.getUniqueId().toString();
            if (plugin.seats().freeSeats(id, seats) > 0) {
                plugin.seats().sitAnywhere(player, id, game.id(), d.getLocation());
                return;
            }
        }
        m.send(player, "menu.machines-full", "game", game.displayName());
    }

    /** A spot on the floor in front of the machine (the side the admin stood on), facing it. */
    private Location front(ItemDisplay display, String game) {
        Box box = box(display, game);
        float yaw = display.getLocation().getYaw();
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        double cx = (box.x0() + box.x1()) / 2, cz = (box.z0() + box.z1()) / 2;
        double halfDepth = (Math.abs(fx) > 0.5 ? box.x1() - box.x0() : box.z1() - box.z0()) / 2;
        double out = halfDepth + 1.0;
        return new Location(display.getWorld(), cx + fx * out, Math.floor(box.y0() + 0.01) + 0.01, cz + fz * out, yaw + 180f, 10f);
    }

    void open(Player player, String id) {
        if (plugin.access().busyElsewhere(player, id)) return;
        Game game = plugin.games().get(id);
        if (game == null || !game.enabled()) {
            plugin.messages().send(player, "game.disabled");
            return;
        }
        if (game.permitted(player) && plugin.access().canPlay(player, game.casinoOnly(), false)) game.open(player, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Interaction interaction)) return;
        PersistentDataContainer pdc = interaction.getPersistentDataContainer();
        String id = pdc.get(gameKey, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        String machine = pdc.get(displayKey, PersistentDataType.STRING);
        Integer seat = pdc.get(seatKey, PersistentDataType.INTEGER);
        if (machine != null && seat != null) {
            plugin.seats().sit(e.getPlayer(), machine, id, seat, interaction.getLocation());
        } else {
            clickModel(e.getPlayer(), machine, id, interaction.getLocation());
        }
    }

    /** The model (or a barrier): a random free chair, or the game directly when the machine has no chairs. */
    private void clickModel(Player player, String machine, String game, Location near) {
        if (machine != null && seatCount(game) > 0) plugin.seats().sitAnywhere(player, machine, game, near);
        else open(player, game);
    }

    /** A barrier of a machine (where no click box reaches, e.g. the top of a low table) opens the game too. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBarrierClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block block = e.getClickedBlock();
        if (block == null || block.getType() != Material.BARRIER) return;
        String key = block.getX() + "," + block.getY() + "," + block.getZ();
        for (Entity en : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 6, 6, 6)) {
            if (!(en instanceof ItemDisplay d)) continue;
            PersistentDataContainer pdc = d.getPersistentDataContainer();
            String game = pdc.get(gameKey, PersistentDataType.STRING);
            String barriers = pdc.get(barriersKey, PersistentDataType.STRING);
            if (game == null || barriers == null) continue;
            for (String p : barriers.split(";")) {
                if (p.equals(key)) {
                    e.setCancelled(true);
                    clickModel(e.getPlayer(), d.getUniqueId().toString(), game, block.getLocation());
                    return;
                }
            }
        }
    }
}

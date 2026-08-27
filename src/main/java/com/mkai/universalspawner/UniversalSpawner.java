package com.mkai.universalspawner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod(UniversalSpawner.MOD_ID)
public class UniversalSpawner {
    public static final String MOD_ID = "universalspawner";
    public static final String MINEPIECE = "minepiece";
    public static final String EVOLUTION_TAG = "UniversalEvolutionEligible";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> DEVELOPMENT_STONE = ITEMS.register(
            "development_stone", () -> new Item(new Item.Properties().stacksTo(16)));

    private static final Random RNG = new Random();
    private static final Map<ResourceLocation, ResourceLocation> EVOLUTION = new HashMap<>();
    private static final Set<String> NON_CHARACTER = Set.of(
            "projectile", "bullet", "meteor", "fist", "slash", "sweep", "impact", "wave",
            "energy", "flame_ball", "ice_ball", "dark_ball", "dynamite", "missile", "arrow",
            "crack", "gomu_arm", "dyna_stones", "tornado", "dragon_head", "dog_head", "note",
            "partisan", "gryphon", "hollow", "negative", "muso_donatsu", "tot_musica", "rock_fragment",
            "rubble", "tree_root", "pad", "heart_bullet", "booger", "strike"
    );

    private final Map<UUID, Long> lastSpawnByPlayer = new ConcurrentHashMap<>();

    public UniversalSpawner() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SpawnConfig.SPEC);
        ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES ||
                e.getTabKey() == CreativeModeTabs.INGREDIENTS) {
                e.accept(DEVELOPMENT_STONE);
            }
        });
        MinecraftForge.EVENT_BUS.register(this);
        buildEvolutionMap();
    }

    private void buildEvolutionMap() {
        addChain("luffy_east_blue", "luffy_1", "luffy_2_years_later", "luffy_wano_country");
        addChain("zoro_east_blue", "zoro_1", "zoro_2_years_later", "zoro_onigashima");
        addChain("sanji_east_blue", "sanji_1", "sanji_2_years_later", "sanji_onigashima");
        addChain("trafalgar_d_water_law_grand_line", "trafalgar_d_water_law_seven_warlords_of_the_sea", "trafalgar_d_water_law_wano_country");
        // Gracefully support likely aliases if a future Mine Piece build changes one Law id.
        addAliasChainPrefix("trafalgar_d_water_law", "grand_line", "seven_warlords_of_the_sea", "wano_country");
    }

    private void addChain(String... ids) {
        for (int i = 0; i + 1 < ids.length; i++) {
            EVOLUTION.put(new ResourceLocation(MINEPIECE, ids[i]), new ResourceLocation(MINEPIECE, ids[i + 1]));
        }
    }

    private void addAliasChainPrefix(String prefix, String... forms) {
        for (int i = 0; i + 1 < forms.length; i++) {
            EVOLUTION.put(new ResourceLocation(MINEPIECE, prefix + "_" + forms[i]),
                    new ResourceLocation(MINEPIECE, prefix + "_" + forms[i + 1]));
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = System.currentTimeMillis();
        if (!SpawnConfig.ENABLED.get()) return;
        if (!ModList.get().isLoaded(MINEPIECE)) return;
        for (var serverPlayer : Objects.requireNonNull(event.getServer()).getPlayerList().getPlayers()) {
            long last = lastSpawnByPlayer.getOrDefault(serverPlayer.getUUID(), 0L);
            // ~1 check per 2 seconds per player; real spawn is much rarer.
            if (now - last < SpawnConfig.CHECK_INTERVAL_SECONDS.get() * 1000L) continue;
            lastSpawnByPlayer.put(serverPlayer.getUUID(), now);
            tryNaturalSpawn(serverPlayer);
        }
    }

    private void tryNaturalSpawn(net.minecraft.server.level.ServerPlayer player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        if (player.isSpectator()) return;
        if (RNG.nextDouble() > SpawnConfig.NORMAL_CHANCE.get()) return;

        Biome biome = level.getBiome(player.blockPosition()).value();
        ResourceLocation biomeId = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getKey(biome);
        String biomeName = biomeId == null ? "" : biomeId.toString();

        List<EntityType<?>> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (id == null || !MINEPIECE.equals(id.getNamespace())) continue;
            if (!isNaturalCharacter(type, id)) continue;
            if (!canSpawnHere(id, biomeName)) continue;
            double weight = spawnWeight(id);
            if (weight > 0) {
                candidates.add(type);
                weights.add(weight);
            }
        }
        if (candidates.isEmpty()) return;

        EntityType<?> chosen = weighted(candidates, weights);
        if (chosen == null) return;

        // Main Straw Hat forms are intentionally rare.
        ResourceLocation chosenId = ForgeRegistries.ENTITY_TYPES.getKey(chosen);
        if (chosenId == null) return;
        if (isMajorCharacter(chosenId) && RNG.nextDouble() > SpawnConfig.MAJOR_CHANCE.get()) return;

        AABB nearbyBox = player.getBoundingBox().inflate(64.0D);
        long nearby = level.getEntitiesOfClass(Mob.class, nearbyBox,
                mob -> {
                    ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
                    return id != null && MINEPIECE.equals(id.getNamespace()) && isNaturalCharacter(mob.getType(), id);
                }).size();
        if (nearby >= SpawnConfig.MAX_NEARBY.get()) return;

        double angle = RNG.nextDouble() * Math.PI * 2;
        double distance = SpawnConfig.SPAWN_RADIUS_MIN.get() + RNG.nextDouble() *
                (SpawnConfig.SPAWN_RADIUS_MAX.get() - SpawnConfig.SPAWN_RADIUS_MIN.get());
        double x = player.getX() + Math.cos(angle) * distance;
        double z = player.getZ() + Math.sin(angle) * distance;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(x), (int) Math.floor(z));
        if (Math.abs(y - player.getY()) > 40) return;

        Entity entity = chosen.create(level);
        if (!(entity instanceof Mob mob)) return;
        mob.moveTo(x, y + 0.1D, z, RNG.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(mob)) return;
        level.addFreshEntity(mob);
    }

    private boolean isNaturalCharacter(EntityType<?> type, ResourceLocation id) {
        String n = id.getPath().toLowerCase(Locale.ROOT);
        for (String bad : NON_CHARACTER) if (n.contains(bad)) return false;
        // Do not naturally spawn later forms: these are obtained through the Development Stone.
        if (n.endsWith("_2_years_later") || n.contains("_wano_country") || n.contains("_onigashima")) return false;
        if (n.equals("luffy_1") || n.equals("zoro_1") || n.equals("sanji_1")) return true;
        return Mob.class.isAssignableFrom(type.getBaseClass());
    }

    private boolean canSpawnHere(ResourceLocation id, String biome) {
        String n = id.getPath().toLowerCase(Locale.ROOT);
        String b = biome.toLowerCase(Locale.ROOT);
        // East Blue / generic sea & coastal characters.
        if (n.contains("luffy") || n.contains("zoro") || n.contains("sanji") || n.contains("nami") || n.contains("usopp")) {
            return b.contains("ocean") || b.contains("beach") || b.contains("plains") || b.contains("forest") || b.contains("river");
        }
        // Marines and government characters: coast/plains.
        if (n.contains("marine") || n.contains("smoker") || n.contains("tashigi") || n.contains("koby") || n.contains("garp") || n.contains("akainu") || n.contains("aokiji") || n.contains("kizaru")) {
            return b.contains("ocean") || b.contains("beach") || b.contains("plains") || b.contains("savanna");
        }
        // Wano/Kaido-side characters prefer warmer/wooded mountainous biomes.
        if (n.contains("yamato") || n.contains("kaidou") || n.contains("kaido") || n.contains("oden") || n.contains("king") || n.contains("queen") || n.contains("jack")) {
            return b.contains("taiga") || b.contains("forest") || b.contains("jungle") || b.contains("badlands") || b.contains("mountain") || b.contains("hill");
        }
        // Fishmen / sea creatures.
        if (n.contains("arlong") || n.contains("jinbe") || n.contains("hody") || n.contains("kuroobi") || n.contains("hatchan")) {
            return b.contains("ocean") || b.contains("river") || b.contains("beach");
        }
        // Desert-appropriate characters.
        if (n.contains("crocodile") || n.contains("alvida") || n.contains("bellamy") || n.contains("mr1") || n.contains("mr5") || n.contains("mr9")) {
            return b.contains("desert") || b.contains("savanna") || b.contains("badlands");
        }
        // Most other Mine Piece NPCs: only non-frozen overworld biomes.
        return !b.contains("nether") && !b.contains("end") && !b.contains("deep_dark") &&
                !b.contains("snow") && !b.contains("ice") && !b.contains("frozen");
    }

    private double spawnWeight(ResourceLocation id) {
        String n = id.getPath().toLowerCase(Locale.ROOT);
        if (isMajorCharacter(id)) return 0.10D;
        if (n.contains("marine") || n.contains("pirate") || n.contains("bandit")) return 1.3D;
        if (n.contains("fish") || n.contains("whale") || n.contains("bear")) return 0.8D;
        return 1.0D;
    }

    private boolean isMajorCharacter(ResourceLocation id) {
        String n = id.getPath();
        return n.equals("luffy_1") || n.equals("luffy_east_blue") ||
               n.equals("zoro_1") || n.equals("zoro_east_blue") ||
               n.equals("sanji_1") || n.equals("sanji_east_blue") ||
               n.equals("trafalgar_d_water_law_grand_line") ||
               n.equals("mihawk") || n.equals("shanks") ||
               n.equals("portgas_d_ace") || n.equals("ace_1");
    }

    private <T> T weighted(List<T> list, List<Double> weights) {
        double total = 0;
        for (double w : weights) total += w;
        double r = RNG.nextDouble() * total;
        for (int i = 0; i < list.size(); i++) {
            r -= weights.get(i);
            if (r <= 0) return list.get(i);
        }
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    @SubscribeEvent
    public void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getTarget() instanceof Mob mob)) return;
        ItemStack stack = event.getItemStack();
        if (!stack.is(DEVELOPMENT_STONE.get())) return;
        if (!SpawnConfig.ENABLED.get()) return;
        if (!ModList.get().isLoaded(MINEPIECE)) return;

        ResourceLocation current = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (current == null || !MINEPIECE.equals(current.getNamespace())) return;
        ResourceLocation next = EVOLUTION.get(current);
        if (next == null) return;

        EntityType<?> nextType = ForgeRegistries.ENTITY_TYPES.getValue(next);
        if (nextType == null) return;

        Entity created = nextType.create(mob.level());
        if (!(created instanceof Mob newMob)) return;

        CompoundTagCopy.copyPersistentData(mob, newMob);
        newMob.setUUID(mob.getUUID());
        newMob.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        float ratio = mob.getMaxHealth() <= 0 ? 1.0F : mob.getHealth() / mob.getMaxHealth();
        mob.remove(Entity.RemovalReason.DISCARDED);
        mob.level().addFreshEntity(newMob);
        newMob.setHealth(Math.max(1.0F, Math.min(newMob.getMaxHealth(), newMob.getMaxHealth() * ratio)));

        if (!event.getEntity().getAbilities().instabuild) stack.shrink(1);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        event.setCanceled(true);
    }


    private Component getEvolutionDisplayName(ResourceLocation id) {
        String path = id.getPath();
        return switch (path) {
            case "luffy_east_blue" -> Component.literal("Luffy - East Blue");
            case "luffy_1" -> Component.literal("Luffy");
            case "luffy_2_years_later" -> Component.literal("Luffy - 2 Years Later");
            case "luffy_wano_country" -> Component.literal("Luffy - Wano");
            case "zoro_east_blue" -> Component.literal("Zoro - East Blue");
            case "zoro_1" -> Component.literal("Zoro");
            case "zoro_2_years_later" -> Component.literal("Zoro - 2 Years Later");
            case "zoro_onigashima" -> Component.literal("Zoro - Onigashima");
            case "sanji_east_blue" -> Component.literal("Sanji - East Blue");
            case "sanji_1" -> Component.literal("Sanji");
            case "sanji_2_years_later" -> Component.literal("Sanji - 2 Years Later");
            case "sanji_onigashima" -> Component.literal("Sanji - Onigashima");
            case "trafalgar_d_water_law_grand_line" -> Component.literal("Trafalgar D. Water Law - Grand Line");
            case "trafalgar_d_water_law_seven_warlords_of_the_sea" -> Component.literal("Trafalgar D. Water Law - Warlord");
            case "trafalgar_d_water_law_wano_country" -> Component.literal("Trafalgar D. Water Law - Wano");
            default -> null;
        };
    }

    static final class CompoundTagCopy {
        static void copyPersistentData(Entity from, Entity to) {
            net.minecraft.nbt.CompoundTag source = from.getPersistentData().copy();
            net.minecraft.nbt.CompoundTag target = to.getPersistentData();
            for (String key : source.getAllKeys()) target.put(key, source.get(key).copy());
        }
    }
}

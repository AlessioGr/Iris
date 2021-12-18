/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.core.nms.v18;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMSBinding;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.math.BlockPosition;
import com.volmit.iris.util.nbt.io.NBTUtil;
import com.volmit.iris.util.nbt.mca.NBTWorld;
import com.volmit.iris.util.nbt.mca.palette.MCABiomeContainer;
import com.volmit.iris.util.nbt.mca.palette.MCAChunkBiomeContainer;
import com.volmit.iris.util.nbt.mca.palette.MCAGlobalPalette;
import com.volmit.iris.util.nbt.mca.palette.MCAIdMap;
import com.volmit.iris.util.nbt.mca.palette.MCAIdMapper;
import com.volmit.iris.util.nbt.mca.palette.MCAPalette;
import com.volmit.iris.util.nbt.mca.palette.MCAPaletteAccess;
import com.volmit.iris.util.nbt.mca.palette.MCAPalettedContainer;
import com.volmit.iris.util.nbt.mca.palette.MCAWrappedPalettedContainer;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.nbt.NbtIo;
//import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//import net.minecraft.world.level.chunk.BiomeStorage;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import org.bukkit.craftbukkit.v1_18_R1.CraftServer;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_18_R1.entity.CraftEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class NMSBinding18 implements INMSBinding {
    private final BlockData AIR = Material.AIR.createBlockData();
    private final KMap<org.bukkit.block.Biome, Object> baseBiomeCache = new KMap<>();
    private final AtomicCache<MCAIdMapper<BlockState>> registryCache = new AtomicCache<>();
    private final AtomicCache<MCAPalette<BlockState>> globalCache = new AtomicCache<>();
    private final AtomicCache<MCAIdMap<Biome>> biomeMapCache = new AtomicCache<>();
    private Field biomeStorageCache = null;

    public boolean supportsDataPacks() {
        return true;
    }

    @Override
    public MCAPaletteAccess createPalette() {
        MCAIdMapper<BlockState> registry = registryCache.aquireNasty(() -> {
            Field cf = net.minecraft.core.IdMapper.class.getDeclaredField("tToId");
            Field df = net.minecraft.core.IdMapper.class.getDeclaredField("idToT");
            Field bf = net.minecraft.core.IdMapper.class.getDeclaredField("nextId");
            cf.setAccessible(true);
            df.setAccessible(true);
            bf.setAccessible(true);
            net.minecraft.core.IdMapper<BlockState> blockData = Block.BLOCK_STATE_REGISTRY;
            int b = bf.getInt(blockData);
            IdentityHashMap<BlockState, Integer> c = (IdentityHashMap<BlockState, Integer>) cf.get(blockData);
            List<BlockState> d = (List<BlockState>) df.get(blockData);
            return new MCAIdMapper<>(c, d, b);
        });
        MCAPalette<BlockState> global = globalCache.aquireNasty(() -> new MCAGlobalPalette<>(registry, ((CraftBlockData) AIR).getState()));
        MCAPalettedContainer<BlockState> container = new MCAPalettedContainer<>(global, registry,
                i -> ((CraftBlockData) NBTWorld.getBlockData(i)).getState(),
                i -> NBTWorld.getCompound(CraftBlockData.fromData(i)),
                ((CraftBlockData) AIR).getState());
        return new MCAWrappedPalettedContainer<>(container,
                i -> NBTWorld.getCompound(CraftBlockData.fromData(i)),
                i -> ((CraftBlockData) NBTWorld.getBlockData(i)).getState());
    }

    private Object getBiomeStorage(ChunkGenerator.BiomeGrid g) {
        try {
            return getFieldForBiomeStorage(g).get(g);
        } catch (IllegalAccessException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public boolean hasTile(Location l) {
        return ((CraftWorld) l.getWorld()).getHandle().getBlockEntity(new BlockPos(l.getBlockX(), l.getBlockY(), l.getBlockZ()), false) != null;
    }

    @Override
    public CompoundTag serializeTile(Location location) {
        BlockEntity blockEntity = ((CraftWorld) location.getWorld()).getHandle().getBlockEntity(new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()), true);

        if (blockEntity == null) {
            return null;
        }

        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        blockEntity.load(tag);
        //blockEntity.save(tag);
        return convert(tag);
    }

    @Override
    public void deserializeTile(CompoundTag s, Location newPosition) {
        net.minecraft.nbt.CompoundTag c = convert(s);

        if (c != null) {
            int x = newPosition.getBlockX();
            int y = newPosition.getBlockY();
            int z = newPosition.getBlockZ();
            ServerLevel w = ((CraftWorld) newPosition.getWorld()).getHandle();
            LevelChunk ch = w.getChunkAt(new BlockPos(x >> 4, 0, z >> 4));
            //LevelChunk ch = w.getChunkAt(x >> 4, z >> 4);
            LevelChunkSection sect = ch.getSections()[y >> 4];
            BlockState block = sect.getStates().get(x & 15, y & 15, z & 15);
            //BlockState block = sect.getBlocks().a(x & 15, y & 15, z & 15);
            BlockPos pos = new BlockPos(x, y, z);
            ch.addAndRegisterBlockEntity( BlockEntity.loadStatic(pos, block, c));
            //ch.b(BlockEntity.create(pos, block, c));
        }
    }

    private net.minecraft.nbt.CompoundTag convert(CompoundTag tag) {
        try {
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            NBTUtil.write(tag, boas, false);
            DataInputStream din = new DataInputStream(new ByteArrayInputStream(boas.toByteArray()));
            net.minecraft.nbt.CompoundTag c = NbtIo.read((DataInput) din);
            din.close();
            return c;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    private CompoundTag convert(net.minecraft.nbt.CompoundTag tag) {
        try {
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(boas);
            NbtIo.write(tag, (DataOutput) dos);
            //NbtIo.writeCompressed(tag, (DataOutput) dos); //TODO ??
            dos.close();
            return (CompoundTag) NBTUtil.read(new ByteArrayInputStream(boas.toByteArray()), false).getTag();
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        return null;
    }

    @Override
    public CompoundTag serializeEntity(org.bukkit.entity.Entity be) {
        Entity entity = ((CraftEntity) be).getHandle();
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        entity.save(tag);
        CompoundTag t = convert(tag);
        t.putInt("btype", be.getType().ordinal());
        return t;
    }

    @Override
    public org.bukkit.entity.Entity deserializeEntity(CompoundTag s, Location newPosition) {

        EntityType type = EntityType.values()[s.getInt("btype")];
        s.remove("btype");
        net.minecraft.nbt.CompoundTag tag = convert(s);
        ListTag pos = tag.getList("Pos", 6);
        pos.add(0, DoubleTag.valueOf(newPosition.getX()));
        pos.add(1, DoubleTag.valueOf(newPosition.getY()));
        pos.add(2, DoubleTag.valueOf(newPosition.getZ()));
        tag.put("Pos", pos);
        org.bukkit.entity.Entity be = newPosition.getWorld().spawnEntity(newPosition, type);
        ((CraftEntity) be).getHandle().load(tag);

        return be;
    }

    @Override
    public boolean supportsCustomHeight() {
        return false;
    }

    private Field getFieldForBiomeStorage(Object storage) {
        Field f = biomeStorageCache;

        if (f != null) {
            return f;
        }
        try {

            f = storage.getClass().getDeclaredField("biome");
            f.setAccessible(true);
            return f;
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
            Iris.error(storage.getClass().getCanonicalName());
        }

        biomeStorageCache = f;
        return null;
    }

    private WritableRegistry<Biome> getCustomBiomeRegistry() {
        DedicatedServer dedicatedServer = ((CraftServer) Bukkit.getServer()).getHandle().getServer();
        //Optional<WritableRegistry<Biome>> w = dedicatedServer.registryAccess().ownedRegistry(Registry.BIOME_REGISTRY);

        return dedicatedServer.registryAccess().ownedRegistryOrThrow(net.minecraft.core.Registry.BIOME_REGISTRY);

        //return ((CraftServer) Bukkit.getServer()).getHandle().getServer().getCustomRegistry().b(net.minecraft.core.Registry.BIOME_REGISTRY);
    }

    @Override
    public Object getBiomeBaseFromId(int id) {
        return getCustomBiomeRegistry().byId(id);
        //return getCustomBiomeRegistry().fromId(id);
    }

    @Override
    public int getTrueBiomeBaseId(Object biomeBase) {
        return getCustomBiomeRegistry().getId((Biome) biomeBase);
    }

    @Override
    public Object getTrueBiomeBase(Location location) {
        return ((CraftWorld) location.getWorld()).getHandle().getNoiseBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public String getTrueBiomeBaseKey(Location location) {
        return getKeyForBiomeBase(getTrueBiomeBase(location));
    }

    @Override
    public boolean supportsCustomBiomes() {
        return true;
    }

    @Override
    public int getMinHeight(World world) {
        return world.getMinHeight();
    }

    @Override
    public Object getCustomBiomeBaseFor(String mckey) {
        try {
            ResourceKey<Biome> resourceKey = ResourceKey.create(net.minecraft.core.Registry.BIOME_REGISTRY, new ResourceLocation(mckey.toLowerCase()));
            return getCustomBiomeRegistry().getOrThrow(resourceKey);
        } catch (Throwable e) {
            Iris.reportError(e);
        }

        return null;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    public String getKeyForBiomeBase(Object biomeBase) {
        return getCustomBiomeRegistry().getResourceKey((Biome) biomeBase).get().location().toString();
        //return getCustomBiomeRegistry().getResourceKey((Biome) biomeBase).get().a().toString();
    }

    @Override
    public Object getBiomeBase(World world, org.bukkit.block.Biome biome) {
        return getBiomeBase(((CraftWorld) world).getHandle().registryAccess().registryOrThrow(Registry.CONFIGURED_FEATURE_REGISTRY), biome);
    }

    private Class<?>[] classify(Object... par) {
        Class<?>[] g = new Class<?>[par.length];
        for (int i = 0; i < g.length; i++) {
            g[i] = par[i].getClass();
        }

        return g;
    }

    private <T> T invoke(Object from, String name, Object... par) {
        try {
            Method f = from.getClass().getDeclaredMethod(name, classify(par));
            f.setAccessible(true);
            //noinspection unchecked
            return (T) f.invoke(from, par);
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    private <T> T invokeStatic(Class<?> from, String name, Object... par) {
        try {
            Method f = from.getDeclaredMethod(name, classify(par));
            f.setAccessible(true);
            //noinspection unchecked
            return (T) f.invoke(null, par);
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    private <T> T getField(Object from, String name) {
        try {
            Field f = from.getClass().getDeclaredField(name);
            f.setAccessible(true);
            //noinspection unchecked
            return (T) f.get(from);
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    private <T> T getStaticField(Class<?> t, String name) {
        try {
            Field f = t.getDeclaredField(name);
            f.setAccessible(true);
            //noinspection unchecked
            return (T) f.get(null);
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Object getBiomeBase(Object registry, org.bukkit.block.Biome biome) {
        Object v = baseBiomeCache.get(biome);

        if (v != null) {
            return v;
        }
        //noinspection unchecked
        v = org.bukkit.craftbukkit.v1_18_R1.block.CraftBlock.biomeToBiomeBase((net.minecraft.core.Registry<Biome>) registry, biome);
        if (v == null) {
            // Ok so there is this new biome name called "CUSTOM" in Paper's new releases.
            // But, this does NOT exist within CraftBukkit which makes it return an error.
            // So, we will just return the ID that the plains biome returns instead.
            //noinspection unchecked
            return org.bukkit.craftbukkit.v1_18_R1.block.CraftBlock.biomeToBiomeBase((net.minecraft.core.Registry<Biome>) registry, org.bukkit.block.Biome.PLAINS);
        }
        baseBiomeCache.put(biome, v);
        return v;
    }

    @Override
    public int getBiomeId(org.bukkit.block.Biome biome) {
        for (World i : Bukkit.getWorlds()) {
            if (i.getEnvironment().equals(World.Environment.NORMAL)) {

                net.minecraft.core.Registry<Biome> registry = ((CraftWorld) i).getHandle().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY); //aO in 1.18 mappings would be CONFIGURED_STRUCTURE_FEATURE_REGISTRY?

                return registry.getId((Biome) getBiomeBase(registry, biome));
            }
        }

        return biome.ordinal();
    }

    private MCAIdMap<Biome> getBiomeMapping() {
        return biomeMapCache.aquire(() -> new MCAIdMap<>() {
            @NotNull
            @Override
            public Iterator<Biome> iterator() {
                return getCustomBiomeRegistry().iterator();
            }

            @Override
            public int getId(Biome paramT) {
                return getCustomBiomeRegistry().getId(paramT);
            }

            @Override
            public Biome byId(int paramInt) {
                return getCustomBiomeRegistry().byId(paramInt);
            } //TODO: byIdOrThrow instead?
        });
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max) {
        MCAChunkBiomeContainer<Biome> base = new MCAChunkBiomeContainer<>(getBiomeMapping(), min, max);
        return getBiomeContainerInterface(getBiomeMapping(), base);
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max, int[] data) {
        MCAChunkBiomeContainer<Biome> base = new MCAChunkBiomeContainer<>(getBiomeMapping(), min, max, data);
        return getBiomeContainerInterface(getBiomeMapping(), base);
    }

    @NotNull
    private MCABiomeContainer getBiomeContainerInterface(MCAIdMap<Biome> biomeMapping, MCAChunkBiomeContainer<Biome> base) {
        return new MCABiomeContainer() {
            @Override
            public int[] getData() {
                return base.writeBiomes();
            }

            @Override
            public void setBiome(int x, int y, int z, int id) {
                base.setBiome(x, y, z, biomeMapping.byId(id));
            }

            @Override
            public int getBiome(int x, int y, int z) {
                return biomeMapping.getId(base.getBiome(x, y, z));
            }
        };
    }

    @Override
    public int countCustomBiomes() {
        AtomicInteger a = new AtomicInteger(0);

        getCustomBiomeRegistry().keySet().forEach((resourceLocation) -> {
            //ResourceLocation k = i. i.getKey().a();

            if (resourceLocation.getNamespace().equals("minecraft")) {
                return;
            }

            a.incrementAndGet();
            Iris.debug("Custom Biome: " + resourceLocation);
        });

        return a.get();
    }

    /*@Override
    public void forceBiomeInto(int x, int y, int z, Object somethingVeryDirty, ChunkGenerator.BiomeGrid chunk) {
        try {
            //BiomeStorage s = (BiomeStorage) getFieldForBiomeStorage(chunk).get(chunk);
            //s.setBiome(x, y, z, (Biome) somethingVeryDirty);
        } catch (IllegalAccessException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }
    }**/


    //TODO: Idek if this is right
    @Override
    public void forceBiomeInto(int x, int y, int z, Object somethingVeryDirty,ChunkGenerator.BiomeGrid chunk) {
        //try {
            chunk.setBiome(x, y, z, (org.bukkit.block.Biome) somethingVeryDirty);
            //s.setBiome(x, y, z, (Biome) somethingVeryDirty);
            //} catch (IllegalAccessException e) {
            //Iris.reportError(e);
            //e.printStackTrace();
            //}
    }

    @Override
    public boolean isBukkit() {
        return false;
    }
}

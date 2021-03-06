package com.tamehumans.entity.ai;

import com.tamehumans.entity.EntityMiner;
import com.tamehumans.utils.InventoryUtils;
import com.tamehumans.utils.Vector3Int;
import net.minecraft.block.Block;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockRedstoneOre;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;

public class EntityAIMineOre extends EntityAIBase {

    private EntityMiner miner;
    private double movementSpeed;
    private float maxSearchDistance;
    private Vector3Int oreLocation;
    private int currentProspectingTime;
    private int maxProspectingTime = 10;
    private int currentMiningTime;
    private int maxMiningTime;


    public EntityAIMineOre(EntityMiner miner, double movementSpeed, float maxSearchDistance, int maxMiningTime) {
        this.miner = miner;
        this.maxSearchDistance = maxSearchDistance;
        this.movementSpeed = movementSpeed;
        this.maxMiningTime = maxMiningTime;
        this.currentMiningTime = maxMiningTime;
        this.currentProspectingTime = this.maxProspectingTime;
        this.setMutexBits(9);
    }

    public boolean shouldExecute() {
        if (!this.miner.isTamed()) return false;

        if (this.miner.getNavigator().getPath() == null) {
            if (this.currentProspectingTime > 0) {
                this.currentProspectingTime--;
                return false;
            }
            else {
                this.currentProspectingTime = this.maxProspectingTime;
            }

            System.out.println("Miner " + this.miner.getEntityId() + " prospecting for ore");
            int minX = MathHelper.floor_double(this.miner.posX - maxSearchDistance);
            int minY = MathHelper.floor_double(this.miner.posY - maxSearchDistance);
            int minZ = MathHelper.floor_double(this.miner.posZ - maxSearchDistance);
            int maxX = MathHelper.floor_double(this.miner.posX + maxSearchDistance);
            int maxY = MathHelper.floor_double(this.miner.posY + maxSearchDistance);
            int maxZ = MathHelper.floor_double(this.miner.posZ + maxSearchDistance);

            PathEntity shortestPath = null;
            Vector3Int oreLocation = null;
            for (int x = minX; x <= maxX; ++x) {
                for (int y = minY; y <= maxY; ++y) {
                    for (int z = minZ; z <= maxZ; ++z) {
                        Block block = this.miner.worldObj.getBlock(x, y, z);
                        if (shouldMineBlock(block, x, y, z)) {
                            PathEntity path = this.miner.getNavigator().getPathToXYZ((double) x, (double) y, (double) z);
                            if (path != null) {
                                if (path.getFinalPathPoint().xCoord <= x+1 && path.getFinalPathPoint().xCoord >= x-1
                                        && path.getFinalPathPoint().yCoord <= y+1 && path.getFinalPathPoint().yCoord >= y-1
                                        && path.getFinalPathPoint().zCoord <= z+1 && path.getFinalPathPoint().zCoord >= z-1) {
                                    if (shortestPath == null || path.getCurrentPathLength() < shortestPath.getCurrentPathLength()) {
                                        shortestPath = path;
                                        oreLocation = new Vector3Int(x, y, z);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (shortestPath != null) {
                System.out.println("Miner " + this.miner.getEntityId() + " found path to ore");
                this.oreLocation = oreLocation;
                this.miner.getNavigator().setPath(shortestPath, this.movementSpeed);
                return true;
            }
        }

        return false;
    }

    public boolean continueExecuting() {
        if (this.miner.getNavigator().getPath() != null) {
            Block block = this.miner.worldObj.getBlock(this.oreLocation.x, this.oreLocation.y, this.oreLocation.z);
            return shouldMineBlock(block, this.oreLocation.x, this.oreLocation.y, this.oreLocation.z);
        }

        System.out.println("Miner " + this.miner.getEntityId() + " has no path to ore anymore!");
        return false;
    }

    public void updateTask() {
        PathNavigate navigator = this.miner.getNavigator();
        if (navigator.getPath() != null && navigator.getPath().isFinished()) {
            if (this.currentMiningTime > 0) {
                this.currentMiningTime--;
            }
            else {
                int x = this.oreLocation.x;
                int y = this.oreLocation.y;
                int z = this.oreLocation.z;
                World world = this.miner.worldObj;
                Block block = world.getBlock(x, y, z);
                if (shouldMineBlock(block, x, y, z)) {
                    System.out.println("Miner " + this.miner.getEntityId() + " mined ore!");
                    ArrayList<ItemStack> items = block.getDrops(world, x, y, z, world.getBlockMetadata(x, y, z), 0);
                    for (ItemStack item : items) {
                        InventoryUtils.addToInventory(item, this.miner.inventory);
                    }
                    world.setBlockToAir(x, y, z);
                    navigator.clearPathEntity();
                    this.miner.swingItem();
                }
            }
        }
    }

    public void resetTask() {
        System.out.println("Miner " + this.miner.getEntityId() + " forgot task!");
        this.oreLocation = null;
        this.currentMiningTime = this.maxMiningTime;
        this.currentProspectingTime = this.maxProspectingTime;
    }

    private boolean shouldMineBlock(Block block, int x, int y, int z) {
        if (block instanceof BlockOre || block instanceof BlockRedstoneOre) {
            InventoryBasic minerInventory = this.miner.inventory;
            if (InventoryUtils.hasRoomInInventory(minerInventory)) {
                return true;
            }
            else {
                World world = this.miner.worldObj;
                ArrayList<ItemStack> items = block.getDrops(world, x, y, z, world.getBlockMetadata(x, y, z), 0);
                for (ItemStack item : items) {
                    if (InventoryUtils.canFitItem(item, minerInventory)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

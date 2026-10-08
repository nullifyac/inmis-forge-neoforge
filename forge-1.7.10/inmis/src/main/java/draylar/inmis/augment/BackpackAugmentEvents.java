package draylar.inmis.augment;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.gameevent.TickEvent;
import draylar.inmis.network.ServerNetworking;
import draylar.inmis.util.InventoryUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockNetherWart;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.ArrowNockEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.world.BlockEvent;
import java.util.Iterator;
import java.util.ArrayList;

public final class BackpackAugmentEvents {
    private final java.util.Queue<Runnable> afterHarvest=new java.util.ArrayDeque<>();
    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END){ServerNetworking.drain();Runnable task;while((task=afterHarvest.poll())!=null)task.run();}}
    @SubscribeEvent public void playerTick(TickEvent.PlayerTickEvent event){if(event.phase==TickEvent.Phase.END&&!event.player.worldObj.isRemote)BackpackAugmentHandler.tick(event.player);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void pickup(EntityItemPickupEvent event){
        if(event.entityPlayer.worldObj.isRemote||event.item.delayBeforeCanPickup>0)return;
        String owner=event.item.func_145798_i();if(owner!=null&&!owner.equals(event.entityPlayer.getCommandSenderName())&&event.item.lifespan-event.item.age>200)return;
        ItemStack stack=event.item.getEntityItem();if(InventoryUtils.isEmpty(stack))return;
        ItemStack remainder=BackpackAugmentHandler.funnel(event.entityPlayer,stack,false,false);int moved=stack.stackSize-(remainder==null?0:remainder.stackSize);
        if(moved>0){stack.stackSize=remainder==null?0:remainder.stackSize;if(remainder==null)event.setResult(Result.ALLOW);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void drops(LivingDropsEvent event){
        if(event.entityLiving.worldObj.isRemote||event.entityLiving instanceof EntityPlayer||!(event.source.getEntity() instanceof EntityPlayer))return;
        EntityPlayer player=(EntityPlayer)event.source.getEntity();Iterator<EntityItem> iterator=event.drops.iterator();
        while(iterator.hasNext()){EntityItem drop=iterator.next();ItemStack stack=drop.getEntityItem();if(InventoryUtils.isEmpty(stack))continue;
            ItemStack left=BackpackAugmentHandler.funnel(player,stack,true,false);if(left==null)iterator.remove();else drop.setEntityItemStack(left);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void harvest(BlockEvent.HarvestDropsEvent event){
        if(event.world.isRemote||event.harvester==null)return;
        if(!BackpackAugmentHandler.eligible(event.harvester,BackpackAugmentType.LOOTBOUND).isEmpty()){
            Iterator<ItemStack> iterator=event.drops.iterator();
            while(iterator.hasNext()){ItemStack stack=iterator.next();
                if(event.world.rand.nextFloat()>event.dropChance){iterator.remove();continue;}
                ItemStack left=BackpackAugmentHandler.funnel(event.harvester,stack,true,true);if(left==null)iterator.remove();else stack.stackSize=left.stackSize;
            }event.dropChance=1F;
        }
        if((event.block instanceof BlockCrops&&event.blockMetadata>=7)||(event.block instanceof BlockNetherWart&&event.blockMetadata>=3)){
            afterHarvest.offer(()->BackpackAugmentHandler.replant(event.harvester,event.block,event.x,event.y,event.z));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void nock(ArrowNockEvent event){
        if(event.result==null||event.result.getItem()!=Items.bow||event.entityPlayer.capabilities.isCreativeMode)return;
        if(BackpackAugmentHandler.findQuiver(event.entityPlayer)!=null){event.entityPlayer.setItemInUse(event.result,event.result.getMaxItemUseDuration());event.setCanceled(true);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void loose(ArrowLooseEvent event){
        if(event.bow.getItem()!=Items.bow||event.entityPlayer.capabilities.isCreativeMode)return;
        BackpackInventory inventory=BackpackAugmentHandler.findQuiver(event.entityPlayer);if(inventory==null)return;
        event.setCanceled(true);float velocity=event.charge/20F;velocity=(velocity*velocity+velocity*2F)/3F;if(velocity<.1F)return;velocity=Math.min(1F,velocity);
        if(event.entityPlayer.worldObj.isRemote)return;
        EntityArrow arrow=new EntityArrow(event.entityPlayer.worldObj,event.entityPlayer,velocity*2F);if(velocity==1F)arrow.setIsCritical(true);
        int power=EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId,event.bow);if(power>0)arrow.setDamage(arrow.getDamage()+power*.5D+.5D);
        arrow.setKnockbackStrength(EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId,event.bow));if(EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId,event.bow)>0)arrow.setFire(100);
        boolean infinity=EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId,event.bow)>0;
        if(infinity)arrow.canBePickedUp=2;else if(!BackpackAugmentHandler.consumeArrow(inventory))return;
        event.bow.damageItem(1,event.entityPlayer);event.entityPlayer.worldObj.spawnEntityInWorld(arrow);event.entityPlayer.worldObj.playSoundAtEntity(event.entityPlayer,"random.bow",1F,1F/(event.entityPlayer.worldObj.rand.nextFloat()*.4F+1.2F)+velocity*.5F);
    }
}

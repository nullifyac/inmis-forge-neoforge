package draylar.inmis.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Exact native backpack drop/removal hooks and a scoped automatic-placement sound bridge. */
public final class BackpackDeathTransformer implements IClassTransformer {
    private static final Logger LOGGER=LogManager.getLogger("InmisBackpackHooks");
    public static volatile int vanillaHooks,baublesHooks,armorHooks,placementHooks;
    public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null)return null;
        boolean vanilla="net.minecraft.entity.player.InventoryPlayer".equals(transformedName);
        boolean baubles="baubles.common.container.InventoryBaubles".equals(transformedName);
        boolean armor="net.minecraft.inventory.ContainerPlayer$1".equals(transformedName);
        boolean placement="net.minecraft.item.ItemBlock".equals(transformedName);
        if(!vanilla&&!baubles&&!armor&&!placement)return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int count=0;
        if(placement){
            for(Object raw:node.methods){MethodNode method=(MethodNode)raw;
                if(!(method.name.equals("onItemUse")||method.name.equals("func_77648_a")||method.name.equals("a")))continue;
                for(AbstractInsnNode instruction=method.instructions.getFirst(),next;instruction!=null;instruction=next){
                    next=instruction.getNext();if(!(instruction instanceof MethodInsnNode))continue;MethodInsnNode call=(MethodInsnNode)instruction;
                    if(call.owner.equals("net/minecraft/world/World")&&(call.name.equals("playSoundEffect")||call.name.equals("func_72908_a"))&&call.desc.equals("(DDDLjava/lang/String;FF)V")){
                        method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,"draylar/inmis/augment/PlacementSounds","playSound","(Lnet/minecraft/world/World;DDDLjava/lang/String;FF)V",false));count++;
                    }
                }
            }
            if(count!=1)throw new IllegalStateException("Inmis native ItemBlock placement sound hook expected 1 site, found "+count);
            placementHooks=count;LOGGER.info("INMIS_NATIVE_HOOKS {} sites={}",transformedName,count);ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
        }
        if(armor){
            String methodName=InmisLoadingPlugin.obfuscated?"func_82869_a":"canTakeStack";
            for(Object raw:node.methods){MethodNode existing=(MethodNode)raw;if(existing.name.equals(methodName)&&existing.desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;)Z"))
                throw new IllegalStateException("Inmis armor slot hook found an existing removal override");}
            MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC,methodName,"(Lnet/minecraft/entity/player/EntityPlayer;)Z",null,null);
            method.visitCode();method.visitVarInsn(Opcodes.ALOAD,0);method.visitVarInsn(Opcodes.ALOAD,1);
            method.visitMethodInsn(Opcodes.INVOKESTATIC,"draylar/inmis/augment/BackpackDeathBridge","canTakeArmor","(Lnet/minecraft/inventory/Slot;Lnet/minecraft/entity/player/EntityPlayer;)Z",false);
            method.visitInsn(Opcodes.IRETURN);method.visitMaxs(2,2);method.visitEnd();node.methods.add(method);armorHooks=1;
            LOGGER.info("INMIS_NATIVE_HOOKS {} sites=1",transformedName);ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
        }
        for(Object raw:node.methods){MethodNode method=(MethodNode)raw;
            if(vanilla&&!(method.desc.equals("()V")&&(method.name.equals("dropAllItems")||method.name.equals("func_70436_m")||method.name.equals("m"))))continue;
            if(baubles&&!((method.name.equals("dropItems")&&method.desc.equals("(Ljava/util/ArrayList;)V"))
                    ||(method.name.equals("dropItemsAt")&&method.desc.equals("(Ljava/util/ArrayList;Lnet/minecraft/entity/Entity;)V"))))continue;
            for(AbstractInsnNode instruction=method.instructions.getFirst(),next;instruction!=null;instruction=next){
                next=instruction.getNext();
                if(!(instruction instanceof MethodInsnNode))continue;MethodInsnNode call=(MethodInsnNode)instruction;
                if(vanilla&&(call.owner.equals("net/minecraft/entity/player/EntityPlayer")||call.owner.equals("yz"))
                        &&(call.name.equals("func_146097_a")||call.name.equals("a"))
                        &&(call.desc.equals("(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/item/EntityItem;")||call.desc.equals("(Ladd;ZZ)Lxk;"))){
                    String player=call.owner.equals("yz")?"yz":"net/minecraft/entity/player/EntityPlayer";
                    String desc="(L"+player+";"+call.desc.substring(1);
                    method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,"draylar/inmis/augment/BackpackDeathBridge","dropVanilla",desc,false));count++;
                }else if(baubles&&call.owner.equals("java/util/ArrayList")&&call.name.equals("add")&&call.desc.equals("(Ljava/lang/Object;)Z")){
                    method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,"draylar/inmis/augment/BackpackDeathBridge","addBaublesDrop","(Ljava/util/ArrayList;Ljava/lang/Object;)Z",false));count++;
                }
            }
        }
        if(count!=2){
            if(baubles){LOGGER.warn("Unsupported Baubles drop implementation: native backpack spill hook disabled (expected 2 sites, found {})",count);return bytes;}
            throw new IllegalStateException("Inmis native InventoryPlayer.dropAllItems hook expected 2 sites, found "+count);
        }
        if(vanilla)vanillaHooks=count;else baublesHooks=count;
        LOGGER.info("INMIS_NATIVE_HOOKS {} sites={}",transformedName,count);
        ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
}

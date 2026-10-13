package com.corazondemelon.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/** Persistent virtual storage. Stacks are merged by item+components and are not limited by normal inventory slots. */
public final class MaoStorage {
    private static final String KEY = "cdm_infinite_storage";
    private MaoStorage() {}

    private static ListTag getList(MaoEntity mao) { return mao.getPersistentData().getList(KEY, 10); }

    public static void add(MaoEntity mao, ItemStack incoming) {
        if (incoming.isEmpty()) return;
        ListTag list = getList(mao);
        ItemStack remaining = incoming.copy();
        for (int i=0;i<list.size() && !remaining.isEmpty();i++) {
            CompoundTag tag=list.getCompound(i); ItemStack stored=ItemStack.of(tag);
            if (ItemStack.isSameItemSameTags(stored,remaining)) {
                int max=Math.max(64,stored.getMaxStackSize());
                int room=Math.max(0,max-stored.getCount());
                if(room>0){int n=Math.min(room,remaining.getCount());stored.grow(n);remaining.shrink(n);list.set(i,stored.save(new CompoundTag()));}
            }
        }
        while(!remaining.isEmpty()) { ItemStack part=remaining.split(Math.min(64,remaining.getCount())); list.add(part.save(new CompoundTag())); }
        mao.getPersistentData().put(KEY,list);
    }

    public static int count(MaoEntity mao) { int n=0; for(int i=0;i<getList(mao).size();i++) n+=ItemStack.of(getList(mao).getCompound(i)).getCount(); return n; }

    public static String summary(MaoEntity mao) {
        ListTag list=getList(mao); if(list.isEmpty()) return "Mi inventario está vacío por ahora.";
        List<String> lines=new ArrayList<>();
        for(int i=0;i<list.size() && lines.size()<12;i++){ItemStack st=ItemStack.of(list.getCompound(i));if(!st.isEmpty())lines.add(st.getCount()+"x "+st.getHoverName().getString());}
        return "Guardo "+count(mao)+" objetos en total. "+String.join(", ",lines)+(list.size()>12?", y más":"");
    }

    /** Uses vanilla/Forge crafting recipes against stored ingredients; the output is returned to infinite storage. */
    public static ItemStack craft(ServerLevel level, MaoEntity mao, String wanted) {
        String query=wanted.toLowerCase(java.util.Locale.ROOT).replace("minecraft:", "").replace(' ', '_');
        query=switch(query) {
            case "antorcha", "antorchas" -> "torch";
            case "mesa", "mesa_de_crafteo", "mesa_de_trabajo" -> "crafting_table";
            case "horno" -> "furnace";
            case "cofre" -> "chest";
            case "palo", "palos" -> "stick";
            case "pico" -> "pickaxe";
            case "pico_de_hierro" -> "iron_pickaxe";
            case "pico_de_diamante" -> "diamond_pickaxe";
            case "espada" -> "sword";
            case "pan" -> "bread";
            case "papel" -> "paper";
            case "bloque_de_hierro" -> "iron_block";
            case "bloque_de_oro" -> "gold_block";
            case "bloque_de_diamante" -> "diamond_block";
            default -> query;
        };
        ListTag original=getList(mao);
        for(RecipeHolder<CraftingRecipe> holder:level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe=holder.value(); ItemStack result=recipe.getResultItem(level.registryAccess());
            String id=BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
            if(!id.contains(query) || result.isEmpty()) continue;
            List<ItemStack> working=new ArrayList<>();
            for(int i=0;i<original.size();i++) working.add(ItemStack.of(original.getCompound(i)));
            CraftingContainer grid=new CraftingContainer(new ContainerListener(){@Override public void containerChanged(Container container){}},3,3);
            List<Ingredient> ingredients=recipe.getIngredients(); boolean possible=true;
            int width=3, height=3, offsetX=0, offsetY=0;
            if(recipe instanceof ShapedRecipe shaped){width=shaped.getWidth();height=shaped.getHeight();}
            if(width>3||height>3)continue;
            for(int i=0;i<ingredients.size();i++) {
                Ingredient ingredient=ingredients.get(i); if(ingredient.isEmpty())continue;
                int slot;
                if(recipe instanceof ShapedRecipe) { int x=i%width, y=i/width; slot=(y+offsetY)*3+(x+offsetX); }
                else { slot=-1; for(int q=0;q<9;q++) if(grid.getItem(q).isEmpty()){slot=q;break;} }
                if(slot<0||slot>=9){possible=false;break;}
                int found=-1;
                for(int j=0;j<working.size();j++) if(!working.get(j).isEmpty()&&ingredient.test(working.get(j))){found=j;break;}
                if(found<0){possible=false;break;}
                grid.setItem(slot,working.get(found).copyWithCount(1)); working.get(found).shrink(1);
            }
            if(!possible||!recipe.matches(grid,level))continue;
            // Consume the same ingredient list from the real persistent store only after a recipe matches.
            ListTag remaining=getList(mao);
            for(Ingredient ingredient:ingredients) if(!ingredient.isEmpty()) {
                for(int j=0;j<remaining.size();j++){ItemStack st=ItemStack.of(remaining.getCompound(j));if(!st.isEmpty()&&ingredient.test(st)){st.shrink(1);remaining.set(j,st.save(new CompoundTag()));break;}}
            }
            ListTag clean=new ListTag();for(int j=0;j<remaining.size();j++){ItemStack st=ItemStack.of(remaining.getCompound(j));if(!st.isEmpty())clean.add(st.save(new CompoundTag()));}
            mao.getPersistentData().put(KEY,clean);
            ItemStack output=recipe.assemble(grid,level.registryAccess()); add(mao,output);
            return output;
        }
        return ItemStack.EMPTY;
    }

    public static boolean inspectLookedAtContainer(ServerPlayer player) {
        HitResult hit=player.pick(6.0D,0.0F,false);
        if(!(hit instanceof BlockHitResult blockHit)) return false;
        BlockPos pos=blockHit.getBlockPos();
        if(!player.level().hasChunkAt(pos) || !player.level().mayInteract(player,pos) || !(player.level().getBlockEntity(pos) instanceof Container container)) return false;
        List<String> found=new ArrayList<>(); int total=0;
        for(int i=0;i<container.getContainerSize();i++) { ItemStack st=container.getItem(i); if(!st.isEmpty()) { total+=st.getCount(); if(found.size()<12) found.add(st.getCount()+"x "+st.getHoverName().getString()); } }
        player.sendSystemMessage(Component.literal(total==0?"He revisado el contenedor: está vacío.":"He revisado el contenedor: "+total+" objetos. "+String.join(", ",found)+(found.size()>=12?", y más":"")));
        return true;
    }

    public static boolean collectLookedAtContainer(ServerPlayer player, MaoEntity mao) {
        HitResult hit=player.pick(6.0D,0.0F,false);
        if(!(hit instanceof BlockHitResult blockHit)) return false;
        BlockPos pos=blockHit.getBlockPos();
        if(!player.level().hasChunkAt(pos) || !player.mayBuild() || !player.level().mayInteract(player,pos) || !(player.level().getBlockEntity(pos) instanceof Container container)) return false;
        int moved=0;
        for(int i=0;i<container.getContainerSize();i++) {
            ItemStack stack=container.getItem(i);
            if(!stack.isEmpty()) { moved+=stack.getCount(); add(mao,stack); container.setItem(i,ItemStack.EMPTY); }
        }
        container.setChanged();
        player.sendSystemMessage(Component.literal(moved==0?"Ese cofre no tenía objetos para recoger.":"He guardado "+moved+" objetos del cofre en mi inventario infinito."));
        return true;
    }

    public static boolean depositToLookedAtContainer(ServerPlayer player, MaoEntity mao) {
        HitResult hit=player.pick(6.0D,0.0F,false);
        if(!(hit instanceof BlockHitResult blockHit)) return false;
        BlockPos pos=blockHit.getBlockPos();
        if(!player.level().hasChunkAt(pos) || !player.mayBuild() || !player.level().mayInteract(player,pos) || !(player.level().getBlockEntity(pos) instanceof Container container)) return false;
        ListTag list=getList(mao); int moved=0;
        for(int i=0;i<list.size();i++) {
            ItemStack stack=ItemStack.of(list.getCompound(i));
            if(stack.isEmpty())continue;
            for(int slot=0;slot<container.getContainerSize()&&!stack.isEmpty();slot++) {
                ItemStack existing=container.getItem(slot);
                if(existing.isEmpty()) { int n=Math.min(stack.getCount(),stack.getMaxStackSize()); ItemStack put=stack.copy();put.setCount(n);container.setItem(slot,put);stack.shrink(n);moved+=n; }
                else if(ItemStack.isSameItemSameTags(existing,stack)&&existing.getCount()<existing.getMaxStackSize()){int n=Math.min(stack.getCount(),existing.getMaxStackSize()-existing.getCount());existing.grow(n);stack.shrink(n);moved+=n;}
            }
            list.set(i,stack.save(new CompoundTag()));
        }
        ListTag clean=new ListTag();for(int i=0;i<list.size();i++){ItemStack st=ItemStack.of(list.getCompound(i));if(!st.isEmpty())clean.add(st.save(new CompoundTag()));}
        mao.getPersistentData().put(KEY,clean);container.setChanged();
        player.sendSystemMessage(Component.literal("He dejado "+moved+" objetos en el cofre."));return true;
    }
}

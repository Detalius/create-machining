package com.jessehandke.createmachining.registry;

import com.jessehandke.createmachining.CreateMachining;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateMachining.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_machining"))
                    .icon(() -> ModItems.CNC_LATHE.get().getDefaultInstance())
                    // the lambda receives the tab's item list, and we add every one of our items to it
                    .displayItems((params, output) -> ModItems.ITEMS.getEntries()
                            .forEach(item -> output.accept(item.get())))
                    .build());

    private ModCreativeTabs() {}
}

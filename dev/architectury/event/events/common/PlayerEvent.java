/*
 * This file is part of architectury.
 * Copyright (C) 2020, 2021, 2022 architectury
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package dev.architectury.event.events.common;

import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;
import net.minecraft.class_1263;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1542;
import net.minecraft.class_1657;
import net.minecraft.class_1703;
import net.minecraft.class_1799;
import net.minecraft.class_1937;
import net.minecraft.class_239;
import net.minecraft.class_3222;
import net.minecraft.class_3966;
import net.minecraft.class_5321;
import net.minecraft.class_8779;
import org.jetbrains.annotations.Nullable;

public interface PlayerEvent {
    /**
     * @see PlayerJoin#join(class_3222)
     */
    Event<PlayerJoin> PLAYER_JOIN = EventFactory.createLoop();
    /**
     * @see PlayerQuit#quit(class_3222)
     */
    Event<PlayerQuit> PLAYER_QUIT = EventFactory.createLoop();
    /**
     * @see PlayerRespawn#respawn(class_3222, boolean, net.minecraft.class_1297.class_5529)
     */
    Event<PlayerRespawn> PLAYER_RESPAWN = EventFactory.createLoop();
    /**
     * @see PlayerAdvancement#award(class_3222, class_8779)
     */
    Event<PlayerAdvancement> PLAYER_ADVANCEMENT = EventFactory.createLoop();
    /**
     * @see PlayerClone#clone(class_3222, class_3222, boolean)
     */
    Event<PlayerClone> PLAYER_CLONE = EventFactory.createLoop();
    /**
     * @see CraftItem#craft(class_1657, class_1799, class_1263)
     */
    Event<CraftItem> CRAFT_ITEM = EventFactory.createLoop();
    /**
     * @see SmeltItem#smelt(class_1657, class_1799)
     */
    Event<SmeltItem> SMELT_ITEM = EventFactory.createLoop();
    /**
     * @see PickupItemPredicate#canPickup(class_1657, class_1542, class_1799)
     */
    Event<PickupItemPredicate> PICKUP_ITEM_PRE = EventFactory.createEventResult();
    /**
     * @see PickupItem#pickup(class_1657, class_1542, class_1799)
     */
    Event<PickupItem> PICKUP_ITEM_POST = EventFactory.createLoop();
    /**
     * @see ChangeDimension#change(class_3222, class_5321, class_5321)
     */
    Event<ChangeDimension> CHANGE_DIMENSION = EventFactory.createLoop();
    /**
     * @see DropItem#drop(class_1657, class_1542)
     */
    Event<DropItem> DROP_ITEM = EventFactory.createEventResult();
    /**
     * @see OpenMenu#open(class_1657, class_1703)
     */
    Event<OpenMenu> OPEN_MENU = EventFactory.createLoop();
    /**
     * @see CloseMenu#close(class_1657, class_1703)
     */
    Event<CloseMenu> CLOSE_MENU = EventFactory.createLoop();
    /**
     * @see FillBucket#fill(class_1657, class_1937, class_1799, class_239)
     */
    Event<FillBucket> FILL_BUCKET = EventFactory.createCompoundEventResult();
    /**
     * @see AttackEntity#attack(class_1657, class_1937, class_1297, class_1268, class_3966)
     */
    Event<AttackEntity> ATTACK_ENTITY = EventFactory.createEventResult();
    
    interface PlayerJoin {
        /**
         * Invoked after a player joined a server level.
         * Equivalent to Forge's {@code PlayerLoggedInEvent} event.
         *
         * @param player The joined player.
         */
        void join(class_3222 player);
    }
    
    interface PlayerQuit {
        /**
         * Invoked after a player logged out of a server level.
         * Equivalent to Forge's {@code PlayerLoggedOutEvent} event.
         *
         * @param player The now logged out player.
         */
        void quit(class_3222 player);
    }
    
    interface PlayerRespawn {
        /**
         * Invoked when a player is respawned (e.g. changing dimension).
         * Equivalent to Forge's {@code PlayerRespawnEvent} event.
         * To manipulate the player use {@link PlayerClone#clone(class_3222, class_3222, boolean)}.
         *
         * @param newPlayer    The respawned player.
         * @param conqueredEnd Whether the player has conquered the end. This is true when the player joined the end and now is leaving it. {@link class_3222#field_13989}
         */
        void respawn(class_3222 newPlayer, boolean conqueredEnd, class_1297.class_5529 removalReason);
    }
    
    interface PlayerClone {
        /**
         * Invoked when a player respawns.
         * This can be used to manipulate the new player.
         * Equivalent to Forge's {@code PlayerEvent.Clone} event.
         *
         * @param oldPlayer The old player.
         * @param newPlayer The new player.
         * @param wonGame   This is true when the player joined the end and now is leaving it. {@link class_3222#field_13989}
         */
        void clone(class_3222 oldPlayer, class_3222 newPlayer, boolean wonGame);
    }
    
    interface PlayerAdvancement {
        /**
         * Invoked when a player gets an advancement.
         * Equivalent to Forge's {@code AdvancementEvent} event.
         *
         * @param player      The player who got the advancement.
         * @param advancement The advancement the player got.
         */
        void award(class_3222 player, class_8779 advancement);
    }
    
    interface CraftItem {
        /**
         * Invoked when a player crafts an item.
         * Equivalent to Forge's {@code ItemCraftedEvent} event.
         * This only applies for the vanilla crafting table (or any crafting table using the {@link net.minecraft.class_1734}) and
         * the player inventory crafting grid.
         * This is invoked when the player takes something out of the result slot.
         *
         * @param player      The player.
         * @param constructed The ItemStack that has been crafted.
         * @param inventory   The inventory of the constructor.
         */
        void craft(class_1657 player, class_1799 constructed, class_1263 inventory);
    }
    
    interface SmeltItem {
        /**
         * Invoked when a player smelts an item.
         * Equivalent to Forge's {@code ItemSmeltedEvent} event.
         * This is invoked when the player takes the stack out of the output slot.
         *
         * @param player  The player.
         * @param smelted The smelt result.
         */
        void smelt(class_1657 player, class_1799 smelted);
    }
    
    interface PickupItemPredicate {
        /**
         * Invoked when a player tries to pickup an {@link class_1542}.
         * Equivalent to Forge's {@code EntityItemPickupEvent} event.
         *
         * @param player The player picking up.
         * @param entity The {@link class_1542} that the player tries to pick up.
         * @param stack  The content of the {@link class_1542}.
         * @return A {@link EventResult} determining the outcome of the event,
         * the execution of the pickup may be cancelled by the result.
         */
        EventResult canPickup(class_1657 player, class_1542 entity, class_1799 stack);
    }
    
    interface PickupItem {
        /**
         * Invoked when a player has picked up an {@link class_1542}.
         * Equivalent to Forge's {@code ItemPickupEvent} event.
         *
         * @param player The player.
         * @param entity The {@link class_1542} that the player picked up.
         * @param stack  The content of the {@link class_1542}.
         */
        void pickup(class_1657 player, class_1542 entity, class_1799 stack);
    }
    
    interface ChangeDimension {
        /**
         * Invoked when a player changes their dimension.
         * Equivalent to Forge's {@code PlayerChangedDimensionEvent} event.
         *
         * @param player   The teleporting player.
         * @param oldLevel The level the player comes from.
         * @param newLevel The level the player teleports into.
         */
        void change(class_3222 player, class_5321<class_1937> oldLevel, class_5321<class_1937> newLevel);
    }
    
    interface DropItem {
        /**
         * Invoked when a player drops an item.
         * Equivalent to Forge's {@code ItemTossEvent} event.
         *
         * @param player The player dropping something.
         * @param entity The entity that has spawned when the player dropped a ItemStack.
         * @return A {@link EventResult} determining the outcome of the event,
         * the execution of the drop may be cancelled by the result.
         */
        EventResult drop(class_1657 player, class_1542 entity);
    }
    
    interface OpenMenu {
        /**
         * Invoked when a player opens a menu.
         * Equivalent to Forge's {@code PlayerContainerEvent.Open} event.
         *
         * @param player The player opening the menu.
         * @param menu   The menu that is opened.
         */
        void open(class_1657 player, class_1703 menu);
    }
    
    interface CloseMenu {
        /**
         * Invoked when a player closes a menu.
         * Equivalent to Forge's {@code PlayerContainerEvent.Close} event.
         *
         * @param player The player closing the menu.
         * @param menu   The menu that is closed.
         */
        void close(class_1657 player, class_1703 menu);
    }
    
    interface FillBucket {
        /**
         * Invoked when a player attempts to fill a bucket using right-click.
         * You can return a non-PASS interaction result to cancel further processing by other mods.
         *
         * @param player The player filling the bucket.
         * @param level  The level the player is in.
         * @param stack  The bucket stack.
         * @param target The target which the player has aimed at.
         * @return A {@link CompoundEventResult} determining the outcome of the event.
         */
        CompoundEventResult<class_1799> fill(class_1657 player, class_1937 level, class_1799 stack, @Nullable class_239 target);
    }
    
    interface AttackEntity {
        /**
         * Invoked when a player is about to attack an entity using left-click.
         * Equivalent to Forge's {@code AttackEntityEvent} and Fabric API's {@code AttackEntityCallback} events.
         *
         * @param player The player attacking the entity.
         * @param level  The level the player is in.
         * @param target The entity about to be attacked.
         * @param hand   The hand the player is using.
         * @param result The entity hit result.
         * @return An {@link EventResult} determining the outcome of the event,
         * the attack may be cancelled by the result.
         */
        EventResult attack(class_1657 player, class_1937 level, class_1297 target, class_1268 hand, @Nullable class_3966 result);
    }
}

/*
 * Requiem
 * Copyright (C) 2017-2021 Ladysnake
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
 * along with this program. If not, see <https://www.gnu.org/licenses>.
 *
 * Linking this mod statically or dynamically with other
 * modules is making a combined work based on this mod.
 * Thus, the terms and conditions of the GNU General Public License cover the whole combination.
 *
 * In addition, as a special exception, the copyright holders of
 * this mod give you permission to combine this mod
 * with free software programs or libraries that are released under the GNU LGPL
 * and with code included in the standard release of Minecraft under All Rights Reserved (or
 * modified versions of such code, with unchanged license).
 * You may copy and distribute such a system following the terms of the GNU GPL for this mod
 * and the licenses of the other code concerned.
 *
 * Note that people who make modified versions of this mod are not obligated to grant
 * this special exception for their modified versions; it is their choice whether to do so.
 * The GNU General Public License gives permission to release a modified version without this exception;
 * this exception also makes it possible to release a modified version which carries forward this exception.
 */
package ladysnake.requiem.core.entity.ability;

import ladysnake.requiem.api.v1.entity.ability.AbilityType;
import ladysnake.requiem.api.v1.entity.ability.DirectAbility;
import ladysnake.requiem.api.v1.entity.ability.MobAbilityController;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class DelegatingDirectAbility<E extends LivingEntity, T extends Entity> implements DirectAbility<E, T> {
    private final LivingEntity owner;
    private final Class<T> targetType;
    private final AbilityType delegatedType;

    public DelegatingDirectAbility(LivingEntity owner, Class<T> targetType, AbilityType delegatedType) {
        this.owner = owner;
        this.targetType = targetType;
        this.delegatedType = delegatedType;
    }

    private MobAbilityController getDelegate() {
        return MobAbilityController.get(this.owner);
    }

    @Override
    public double getRange() {
        return this.getDelegate().getRange(this.delegatedType);
    }

    @Override
    public Class<T> getTargetType() {
        return this.targetType;
    }

    @Override
    public boolean canTarget(T target) {
        return this.getDelegate().canTarget(this.delegatedType, target);
    }

    @Override
    public boolean trigger(T target) {
        return this.getDelegate().useDirect(this.delegatedType, target);
    }

    @Override
    public float getCooldownProgress() {
        return this.getDelegate().getCooldownProgress(this.delegatedType);
    }

    @Override
    public Identifier getIconTexture() {
        return this.getDelegate().getIconTexture(this.delegatedType);
    }

    @Override
    public void update() {
        // NO-OP the delegate is already updated externally
    }

    @Override
    public void writeToPacket(PacketByteBuf buf) {
        // NO-OP the delegate is already synchronized externally
    }

    @Override
    public void readFromPacket(PacketByteBuf buf) {
        // NO-OP the delegate is already synchronized externally
    }
}
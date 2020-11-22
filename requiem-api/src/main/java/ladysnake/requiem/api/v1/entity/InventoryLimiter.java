package ladysnake.requiem.api.v1.entity;

import dev.onyxstudios.cca.api.v3.component.Component;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import net.minecraft.util.Identifier;

public interface InventoryLimiter extends Component {
    ComponentKey<InventoryLimiter> KEY = ComponentRegistry.getOrCreate(new Identifier("requiem", "inventory_limiter"), InventoryLimiter.class);

    void setEnabled(boolean enabled);
    boolean isEnabled();
    void unlock(InventoryPart part);
    void lock(InventoryPart part);
    boolean isLocked(InventoryPart part);
    HotbarAvailability getHotbarAvailability();
    boolean isSlotLocked(int playerSlot);
    boolean isSlotInvisible(int playerSlot);
    InventoryShape getInventoryShape();

    enum HotbarAvailability {
        FULL, HANDS, NONE
    }

}
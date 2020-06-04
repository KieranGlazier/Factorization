package factorization.mechanics;

import factorization.fzds.DeltaChunk;
import factorization.shared.Core;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

public class MechanismsFeature {
    static int deltachunk_channel;

    public static void initialize() {
        if (!DeltaChunk.enabled()) return;
        deltachunk_channel = DeltaChunk.getHammerRegistry().makeChannelFor(Core.modId, "mechanisms", 11, 64, "Hinges & cranks");
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            MechanismClientFeature.initialize();
        }
    }

}

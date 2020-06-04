package factorization.misc;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class MiscNet {
    public static final String tpsChannel = "fzmsc.tps";
    public static final FMLEventChannel channel = NetworkRegistry.INSTANCE.newEventDrivenChannel(tpsChannel);
    
    public MiscNet() {
        MiscellaneousNonsense.net = this;
        channel.register(this);
    }
    
    
    @SubscribeEvent
    public void onPacketData(ClientCustomPacketEvent event) {
        FMLProxyPacket packet = event.packet;
        packet.payload().array();
        try {
            ByteBufInputStream input = new ByteBufInputStream(event.packet.payload());
            MiscellaneousNonsense.proxy.handleTpsReport(input.readFloat());
            input.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return;
    }

   public static FMLProxyPacket makeTpsReportPacket(float tps) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(outputStream);
            output.writeFloat(tps);
            output.flush();
            return new FMLProxyPacket(new PacketBuffer(Unpooled.wrappedBuffer(outputStream.toByteArray())), tpsChannel);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}

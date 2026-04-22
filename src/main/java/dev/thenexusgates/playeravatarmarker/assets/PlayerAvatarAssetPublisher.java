package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.common.util.ArrayUtil;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.setup.AssetFinalize;
import com.hypixel.hytale.protocol.packets.setup.AssetInitialize;
import com.hypixel.hytale.protocol.packets.setup.AssetPart;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;

final class PlayerAvatarAssetPublisher {

    private static final int PART_SIZE = 2_621_440;

    private PlayerAvatarAssetPublisher() {
    }

    static boolean deliver(PlayerRef viewer, String assetPath, byte[] pngBytes, boolean requestRebuild) {
        if (viewer == null || assetPath == null || assetPath.isBlank() || pngBytes == null || pngBytes.length == 0) {
            return false;
        }

        PacketHandler packetHandler = viewer.getPacketHandler();
        if (packetHandler == null) {
            return false;
        }

        try {
            CommonAsset asset = new PlayerAvatarRuntimePngAsset(assetPath, pngBytes);
            byte[] blob = asset.getBlob().join();
            byte[][] parts = ArrayUtil.split(blob, PART_SIZE);
            Packet[] packets = new Packet[parts.length + 2];
            packets[0] = new AssetInitialize(asset.toPacket(), blob.length);
            for (int index = 0; index < parts.length; index++) {
                packets[index + 1] = new AssetPart(parts[index]);
            }
            packets[packets.length - 1] = new AssetFinalize();
            for (Packet packet : packets) {
                packetHandler.write((com.hypixel.hytale.protocol.ToClientPacket) packet);
            }
            if (requestRebuild) {
                packetHandler.writeNoCache(new RequestCommonAssetsRebuild());
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
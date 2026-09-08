package no.minecraft.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NetherFortressGenerator {

    public enum PieceType {
        BRIDGE_X,
        BRIDGE_Z,
        CORRIDOR_X,
        CORRIDOR_Z,
        INTERSECTION_CROSS,
        STAIRS_UP_X,
        STAIRS_UP_Z,
        SPAWNER_ROOM
    }

    public static class FortressPiece {
        public final PieceType type;
        public final int minX, minY, minZ;
        public final int maxX, maxY, maxZ;
        public final int spawnerX, spawnerY, spawnerZ; // only if SPAWNER_ROOM

        public FortressPiece(PieceType type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this(type, minX, minY, minZ, maxX, maxY, maxZ, 0, 0, 0);
        }

        public FortressPiece(PieceType type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int sx, int sy, int sz) {
            this.type = type;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.spawnerX = sx;
            this.spawnerY = sy;
            this.spawnerZ = sz;
        }

        public boolean intersectsChunk(int chunkX, int chunkZ) {
            int cMinX = chunkX * Chunk.SIZE_X;
            int cMaxX = cMinX + Chunk.SIZE_X - 1;
            int cMinZ = chunkZ * Chunk.SIZE_Z;
            int cMaxZ = cMinZ + Chunk.SIZE_Z - 1;
            return !(maxX < cMinX || minX > cMaxX || maxZ < cMinZ || minZ > cMaxZ);
        }
    }

    public static class Fortress {
        public final int originX, originZ;
        public final List<FortressPiece> pieces = new ArrayList<>();
        public final List<int[]> spawnerCoords = new ArrayList<>();

        public Fortress(int originX, int originZ) {
            this.originX = originX;
            this.originZ = originZ;
        }
    }

    // Grid spacing for Nether Fortresses (every 24 chunks)
    public static final int FORTRESS_GRID = 24;

    public static Fortress getFortressForRegion(int regionX, int regionZ, long worldSeed) {
        long fortressSeed = (regionX * 341873128712L) ^ (regionZ * 132897987543L) ^ worldSeed;
        Random rand = new Random(fortressSeed);

        // Random offset within the region
        int chunkX = regionX * FORTRESS_GRID + 4 + rand.nextInt(FORTRESS_GRID - 8);
        int chunkZ = regionZ * FORTRESS_GRID + 4 + rand.nextInt(FORTRESS_GRID - 8);
        int originX = chunkX * Chunk.SIZE_X + 8;
        int originZ = chunkZ * Chunk.SIZE_Z + 8;
        int baseY = 24 + rand.nextInt(4); // Elevation above lava ocean (lava is at y <= 16)

        Fortress fortress = new Fortress(originX, originZ);

        // Procedurally generate connected network of pieces
        // Central Intersection
        int centerSize = 7;
        int halfC = centerSize / 2;
        fortress.pieces.add(new FortressPiece(PieceType.INTERSECTION_CROSS,
                originX - halfC, baseY, originZ - halfC,
                originX + halfC, baseY + 6, originZ + halfC));

        // 4 Wings: North (-Z), South (+Z), East (+X), West (-X)
        // Two wings will lead to Blaze Spawners, other wings lead to corridors, stairs and lookout balconies
        int[] spawnerWings;
        if (rand.nextBoolean()) {
            spawnerWings = new int[]{0, 2}; // North and East
        } else {
            spawnerWings = new int[]{1, 3}; // South and West
        }

        for (int wing = 0; wing < 4; wing++) {
            boolean isSpawnerWing = (wing == spawnerWings[0] || wing == spawnerWings[1]);
            buildWing(fortress, wing, originX, baseY, originZ, isSpawnerWing, rand);
        }

        return fortress;
    }

    private static void buildWing(Fortress fortress, int wing, int startX, int startY, int startZ, boolean hasSpawner, Random rand) {
        // wing: 0 = North (-Z), 1 = South (+Z), 2 = East (+X), 3 = West (-X)
        int curX = startX;
        int curY = startY;
        int curZ = startZ;

        int dx = 0;
        int dz = 0;
        if (wing == 0) dz = -1;
        else if (wing == 1) dz = 1;
        else if (wing == 2) dx = 1;
        else if (wing == 3) dx = -1;

        // Step 1: Initial Bridge segment (18-24 blocks long, 5 wide)
        int bridgeLen = 18 + rand.nextInt(8);
        int bMinX, bMaxX, bMinZ, bMaxZ;
        PieceType pType;
        if (dx != 0) {
            pType = PieceType.BRIDGE_X;
            int x1 = curX + (dx > 0 ? 4 : -bridgeLen - 3);
            int x2 = x1 + bridgeLen - 1;
            bMinX = x1; bMaxX = x2;
            bMinZ = curZ - 2; bMaxZ = curZ + 2;
            curX = dx > 0 ? bMaxX + 1 : bMinX - 1;
        } else {
            pType = PieceType.BRIDGE_Z;
            int z1 = curZ + (dz > 0 ? 4 : -bridgeLen - 3);
            int z2 = z1 + bridgeLen - 1;
            bMinX = curX - 2; bMaxX = curX + 2;
            bMinZ = z1; bMaxZ = z2;
            curZ = dz > 0 ? bMaxZ + 1 : bMinZ - 1;
        }
        fortress.pieces.add(new FortressPiece(pType, bMinX, curY, bMinZ, bMaxX, curY + 4, bMaxZ));

        // Step 2: Intermediate Intersection or Staircase
        boolean useStairs = rand.nextBoolean();
        if (useStairs && curY + 6 <= 45) {
            // Stairs up
            int stairLen = 8;
            PieceType stairType = (dx != 0) ? PieceType.STAIRS_UP_X : PieceType.STAIRS_UP_Z;
            int sMinX, sMaxX, sMinZ, sMaxZ;
            if (dx != 0) {
                int x1 = (dx > 0) ? curX : curX - stairLen + 1;
                sMinX = x1; sMaxX = x1 + stairLen - 1;
                sMinZ = curZ - 2; sMaxZ = curZ + 2;
                curX = (dx > 0) ? sMaxX + 1 : sMinX - 1;
            } else {
                int z1 = (dz > 0) ? curZ : curZ - stairLen + 1;
                sMinX = curX - 2; sMaxX = curX + 2;
                sMinZ = z1; sMaxZ = z1 + stairLen - 1;
                curZ = (dz > 0) ? sMaxZ + 1 : sMinZ - 1;
            }
            fortress.pieces.add(new FortressPiece(stairType, sMinX, curY, sMinZ, sMaxX, curY + 7, sMaxZ));
            curY += 4; // Elevated level
        } else {
            // Secondary cross intersection
            int cSize = 7;
            int half = cSize / 2;
            int cMidX = curX + dx * half;
            int cMidZ = curZ + dz * half;
            fortress.pieces.add(new FortressPiece(PieceType.INTERSECTION_CROSS,
                    cMidX - half, curY, cMidZ - half,
                    cMidX + half, curY + 6, cMidZ + half));
            curX = cMidX + dx * (half + 1);
            curZ = cMidZ + dz * (half + 1);

            // Optional turn branch
            if (rand.nextBoolean()) {
                int turnDx = -dz;
                int turnDz = dx;
                int branchLen = 14;
                int brMinX = (turnDx != 0) ? (turnDx > 0 ? cMidX + half + 1 : cMidX - half - branchLen) : cMidX - 2;
                int brMaxX = (turnDx != 0) ? brMinX + branchLen - 1 : cMidX + 2;
                int brMinZ = (turnDz != 0) ? (turnDz > 0 ? cMidZ + half + 1 : cMidZ - half - branchLen) : cMidZ - 2;
                int brMaxZ = (turnDz != 0) ? brMinZ + branchLen - 1 : cMidZ + 2;
                fortress.pieces.add(new FortressPiece(
                        turnDx != 0 ? PieceType.CORRIDOR_X : PieceType.CORRIDOR_Z,
                        brMinX, curY, brMinZ, brMaxX, curY + 4, brMaxZ));
            }
        }

        // Step 3: Second Corridor/Bridge stretch leading to terminus
        int stretchLen = 14 + rand.nextInt(6);
        int strMinX, strMaxX, strMinZ, strMaxZ;
        if (dx != 0) {
            int x1 = (dx > 0) ? curX : curX - stretchLen + 1;
            strMinX = x1; strMaxX = x1 + stretchLen - 1;
            strMinZ = curZ - 2; strMaxZ = curZ + 2;
            curX = (dx > 0) ? strMaxX + 1 : strMinX - 1;
        } else {
            int z1 = (dz > 0) ? curZ : curZ - stretchLen + 1;
            strMinX = curX - 2; strMaxX = curX + 2;
            strMinZ = z1; strMaxZ = z1 + stretchLen - 1;
            curZ = (dz > 0) ? strMaxZ + 1 : strMinZ - 1;
        }
        fortress.pieces.add(new FortressPiece(
                dx != 0 ? PieceType.BRIDGE_X : PieceType.BRIDGE_Z,
                strMinX, curY, strMinZ, strMaxX, curY + 4, strMaxZ));

        // Step 4: Terminus: Either Blaze Spawner Room or Balcony/Lookout Tower
        if (hasSpawner) {
            int rSize = 9; // 9x9 spawner platform
            int half = rSize / 2;
            int rMidX = curX + dx * half;
            int rMidZ = curZ + dz * half;
            int spawnerX = rMidX;
            int spawnerY = curY + 2;
            int spawnerZ = rMidZ;

            fortress.pieces.add(new FortressPiece(PieceType.SPAWNER_ROOM,
                    rMidX - half, curY, rMidZ - half,
                    rMidX + half, curY + 6, rMidZ + half,
                    spawnerX, spawnerY, spawnerZ));
            fortress.spawnerCoords.add(new int[]{spawnerX, spawnerY, spawnerZ});
        } else {
            // Lookout Balcony / Ending Platform (7x7)
            int rSize = 7;
            int half = rSize / 2;
            int rMidX = curX + dx * half;
            int rMidZ = curZ + dz * half;
            fortress.pieces.add(new FortressPiece(PieceType.INTERSECTION_CROSS,
                    rMidX - half, curY, rMidZ - half,
                    rMidX + half, curY + 5, rMidZ + half));
        }
    }

    public static List<FortressPiece> getPiecesIntersectingChunk(int chunkX, int chunkZ, long worldSeed) {
        List<FortressPiece> result = new ArrayList<>();
        // Check neighboring regions that could reach this chunk
        int rX = Math.floorDiv(chunkX, FORTRESS_GRID);
        int rZ = Math.floorDiv(chunkZ, FORTRESS_GRID);

        for (int rx = rX - 1; rx <= rX + 1; rx++) {
            for (int rz = rZ - 1; rz <= rZ + 1; rz++) {
                Fortress f = getFortressForRegion(rx, rz, worldSeed);
                for (FortressPiece piece : f.pieces) {
                    if (piece.intersectsChunk(chunkX, chunkZ)) {
                        result.add(piece);
                    }
                }
            }
        }
        return result;
    }

    public static List<int[]> getSpawnersNear(float px, float pz, float radius, long worldSeed) {
        List<int[]> result = new ArrayList<>();
        int chunkX = (int) Math.floor(px / Chunk.SIZE_X);
        int chunkZ = (int) Math.floor(pz / Chunk.SIZE_Z);
        int rX = Math.floorDiv(chunkX, FORTRESS_GRID);
        int rZ = Math.floorDiv(chunkZ, FORTRESS_GRID);

        for (int rx = rX - 1; rx <= rX + 1; rx++) {
            for (int rz = rZ - 1; rz <= rZ + 1; rz++) {
                Fortress f = getFortressForRegion(rx, rz, worldSeed);
                for (int[] s : f.spawnerCoords) {
                    float dx = s[0] - px;
                    float dz = s[2] - pz;
                    if (Math.sqrt(dx * dx + dz * dz) <= radius) {
                        result.add(s);
                    }
                }
            }
        }
        return result;
    }

    public static void carveAndBuildFortressPiece(Chunk chunk, FortressPiece piece) {
        int cStartX = chunk.getWorldStartX();
        int cStartZ = chunk.getWorldStartZ();

        int x0 = Math.max(piece.minX, cStartX);
        int x1 = Math.min(piece.maxX, cStartX + Chunk.SIZE_X - 1);
        int z0 = Math.max(piece.minZ, cStartZ);
        int z1 = Math.min(piece.maxZ, cStartZ + Chunk.SIZE_Z - 1);

        for (int wx = x0; wx <= x1; wx++) {
            int lx = wx - cStartX;
            for (int wz = z0; wz <= z1; wz++) {
                int lz = wz - cStartZ;

                switch (piece.type) {
                    case BRIDGE_X -> {
                        // Bridge along X axis: width in Z is 5 (piece.minZ to piece.maxZ)
                        // Floor at minY
                        chunk.setBlock(lx, piece.minY, lz, BlockType.NETHER_BRICKS);
                        // Air space inside
                        for (int y = piece.minY + 1; y <= piece.minY + 3; y++) {
                            chunk.setBlock(lx, y, lz, BlockType.AIR);
                        }
                        // Low parapet walls on sides
                        if (wz == piece.minZ || wz == piece.maxZ) {
                            chunk.setBlock(lx, piece.minY + 1, lz, BlockType.NETHER_BRICKS);
                        }
                        // Support pillar every 6 blocks down to bedrock or existing solid block
                        if (Math.floorMod(wx, 6) == 0 && (wz == piece.minZ + 1 || wz == piece.maxZ - 1)) {
                            buildSupportPillar(chunk, lx, piece.minY - 1, lz);
                        }
                    }
                    case BRIDGE_Z -> {
                        // Bridge along Z axis
                        chunk.setBlock(lx, piece.minY, lz, BlockType.NETHER_BRICKS);
                        for (int y = piece.minY + 1; y <= piece.minY + 3; y++) {
                            chunk.setBlock(lx, y, lz, BlockType.AIR);
                        }
                        if (wx == piece.minX || wx == piece.maxX) {
                            chunk.setBlock(lx, piece.minY + 1, lz, BlockType.NETHER_BRICKS);
                        }
                        if (Math.floorMod(wz, 6) == 0 && (wx == piece.minX + 1 || wx == piece.maxX - 1)) {
                            buildSupportPillar(chunk, lx, piece.minY - 1, lz);
                        }
                    }
                    case CORRIDOR_X -> {
                        // Enclosed corridor along X
                        chunk.setBlock(lx, piece.minY, lz, BlockType.NETHER_BRICKS);
                        chunk.setBlock(lx, piece.maxY, lz, BlockType.NETHER_BRICKS);
                        boolean isWall = (wz == piece.minZ || wz == piece.maxZ);
                        for (int y = piece.minY + 1; y < piece.maxY; y++) {
                            chunk.setBlock(lx, y, lz, isWall ? BlockType.NETHER_BRICKS : BlockType.AIR);
                        }
                    }
                    case CORRIDOR_Z -> {
                        // Enclosed corridor along Z
                        chunk.setBlock(lx, piece.minY, lz, BlockType.NETHER_BRICKS);
                        chunk.setBlock(lx, piece.maxY, lz, BlockType.NETHER_BRICKS);
                        boolean isWall = (wx == piece.minX || wx == piece.maxX);
                        for (int y = piece.minY + 1; y < piece.maxY; y++) {
                            chunk.setBlock(lx, y, lz, isWall ? BlockType.NETHER_BRICKS : BlockType.AIR);
                        }
                    }
                    case INTERSECTION_CROSS -> {
                        // 7x7 arched open crossroads room
                        chunk.setBlock(lx, piece.minY, lz, BlockType.NETHER_BRICKS);
                        // Air inside
                        for (int y = piece.minY + 1; y < piece.maxY; y++) {
                            chunk.setBlock(lx, y, lz, BlockType.AIR);
                        }
                        // Corner pillars and arched roof
                        boolean isCorner = (wx == piece.minX || wx == piece.maxX) && (wz == piece.minZ || wz == piece.maxZ);
                        if (isCorner) {
                            for (int y = piece.minY + 1; y <= piece.maxY; y++) {
                                chunk.setBlock(lx, y, lz, BlockType.NETHER_BRICKS);
                            }
                        }
                        chunk.setBlock(lx, piece.maxY, lz, BlockType.NETHER_BRICKS);

                        // Four solid foundation pillars at corners downwards
                        if (isCorner) {
                            buildSupportPillar(chunk, lx, piece.minY - 1, lz);
                        }
                    }
                    case STAIRS_UP_X -> {
                        // Stairs rising along X from minX to maxX
                        int step = (wx - piece.minX) / 2; // rises 1 block every 2 along X
                        int floorY = piece.minY + step;
                        for (int y = piece.minY; y <= floorY; y++) {
                            chunk.setBlock(lx, y, lz, BlockType.NETHER_BRICKS);
                        }
                        for (int y = floorY + 1; y <= floorY + 3; y++) {
                            if (y < Chunk.SIZE_Y) chunk.setBlock(lx, y, lz, BlockType.AIR);
                        }
                        if (wz == piece.minZ || wz == piece.maxZ) {
                            chunk.setBlock(lx, floorY + 1, lz, BlockType.NETHER_BRICKS);
                        }
                    }
                    case STAIRS_UP_Z -> {
                        // Stairs rising along Z from minZ to maxZ
                        int step = (wz - piece.minZ) / 2;
                        int floorY = piece.minY + step;
                        for (int y = piece.minY; y <= floorY; y++) {
                            chunk.setBlock(lx, y, lz, BlockType.NETHER_BRICKS);
                        }
                        for (int y = floorY + 1; y <= floorY + 3; y++) {
                            if (y < Chunk.SIZE_Y) chunk.setBlock(lx, y, lz, BlockType.AIR);
                        }
                        if (wx == piece.minX || wx == piece.maxX) {
                            chunk.setBlock(lx, floorY + 1, lz, BlockType.NETHER_BRICKS);
                        }
                    }
                    case SPAWNER_ROOM -> {
                        // 9x9 Blaze spawner balcony room:
                        // Raised center platform with stairs leading up to spawner cage
                        chunk.setBlock(lx, piece.minY, lz, BlockType.NETHER_BRICKS);
                        // Air space
                        for (int y = piece.minY + 1; y <= piece.maxY; y++) {
                            chunk.setBlock(lx, y, lz, BlockType.AIR);
                        }
                        // Low parapet nether brick fence around edges with open entrance
                        boolean isEdge = (wx == piece.minX || wx == piece.maxX || wz == piece.minZ || wz == piece.maxZ);
                        if (isEdge) {
                            chunk.setBlock(lx, piece.minY + 1, lz, BlockType.NETHER_BRICKS);
                        }

                        // Raised central 3x3 platform
                        int midX = piece.spawnerX;
                        int midZ = piece.spawnerZ;
                        int distC = Math.max(Math.abs(wx - midX), Math.abs(wz - midZ));
                        if (distC <= 1) {
                            chunk.setBlock(lx, piece.minY + 1, lz, BlockType.NETHER_BRICKS);
                        }

                        // Spawner block placed exactly at spawnerX, spawnerY, spawnerZ
                        if (wx == piece.spawnerX && wz == piece.spawnerZ) {
                            chunk.setBlock(lx, piece.spawnerY, lz, BlockType.SPAWNER);
                        }

                        // Strong corner foundation pillars down to floor/lava
                        if (isEdge && (wx == piece.minX || wx == piece.maxX) && (wz == piece.minZ || wz == piece.maxZ)) {
                            buildSupportPillar(chunk, lx, piece.minY - 1, lz);
                        }
                    }
                }
            }
        }
    }

    private static void buildSupportPillar(Chunk chunk, int lx, int startY, int lz) {
        for (int y = startY; y >= 1; y--) {
            BlockType b = chunk.getBlock(lx, y, lz);
            if (b.isSolid() && b != BlockType.AIR && b != BlockType.LAVA) {
                break;
            }
            chunk.setBlock(lx, y, lz, BlockType.NETHER_BRICKS);
        }
    }
}

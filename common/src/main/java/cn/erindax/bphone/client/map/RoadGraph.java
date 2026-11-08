package cn.erindax.bphone.client.map;

import cn.erindax.bphone.map.MapData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeSet;

public final class RoadGraph {
    private static final double SNAP = 3.0D;
    private static final double MERGE = 0.75D;
    private static final double MAX_ACCESS = 160.0D;

    private final double[] nodeX;
    private final double[] nodeZ;
    private final int[][] neighbors;
    private final double[][] costs;
    private final int[] edgeA;
    private final int[] edgeB;
    private final double[] edgeFactor;

    private RoadGraph(double[] nodeX, double[] nodeZ, int[][] neighbors, double[][] costs, int[] edgeA, int[] edgeB,
                      double[] edgeFactor) {
        this.nodeX = nodeX;
        this.nodeZ = nodeZ;
        this.neighbors = neighbors;
        this.costs = costs;
        this.edgeA = edgeA;
        this.edgeB = edgeB;
        this.edgeFactor = edgeFactor;
    }

    private static final class Segment {
        final double ax;
        final double az;
        final double bx;
        final double bz;
        final int road;
        final int index;
        final double factor;
        final TreeSet<Double> cuts = new TreeSet<>();

        Segment(MapData.Point a, MapData.Point b, int road, int index, double factor) {
            this.ax = a.x() + 0.5D;
            this.az = a.z() + 0.5D;
            this.bx = b.x() + 0.5D;
            this.bz = b.z() + 0.5D;
            this.road = road;
            this.index = index;
            this.factor = factor;
            cuts.add(0.0D);
            cuts.add(1.0D);
        }

        double x(double t) {
            return ax + (bx - ax) * t;
        }

        double z(double t) {
            return az + (bz - az) * t;
        }
    }

    private static double factor(int kind) {
        return switch (kind) {
            case MapData.ROAD_AVENUE -> 0.85D;
            case MapData.ROAD_PATH -> 1.15D;
            default -> 1.0D;
        };
    }

    public static RoadGraph build(List<MapData.Road> roads) {
        List<Segment> segments = new ArrayList<>();
        List<double[]> endpoints = new ArrayList<>();
        for (int roadIndex = 0; roadIndex < roads.size(); roadIndex++) {
            MapData.Road road = roads.get(roadIndex);
            if (road.kind() == MapData.ROAD_RAIL || road.points().size() < 2) {
                continue;
            }
            List<MapData.Point> points = road.points();
            for (int index = 0; index + 1 < points.size(); index++) {
                segments.add(new Segment(points.get(index), points.get(index + 1), roadIndex, index, factor(road.kind())));
            }
            MapData.Point first = points.get(0);
            MapData.Point last = points.get(points.size() - 1);
            endpoints.add(new double[] {first.x() + 0.5D, first.z() + 0.5D, roadIndex});
            endpoints.add(new double[] {last.x() + 0.5D, last.z() + 0.5D, roadIndex});
        }

        for (int i = 0; i < segments.size(); i++) {
            Segment first = segments.get(i);
            for (int j = i + 1; j < segments.size(); j++) {
                Segment second = segments.get(j);
                if (first.road == second.road && Math.abs(first.index - second.index) <= 1) {
                    continue;
                }
                intersect(first, second);
            }
        }

        List<double[]> snapLinks = new ArrayList<>();
        for (double[] endpoint : endpoints) {
            Segment best = null;
            double bestT = 0.0D;
            double bestDistance = SNAP;
            for (Segment segment : segments) {
                if (segment.road == (int) endpoint[2]) {
                    continue;
                }
                double t = project(segment, endpoint[0], endpoint[1]);
                double distance = Math.hypot(segment.x(t) - endpoint[0], segment.z(t) - endpoint[1]);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = segment;
                    bestT = t;
                }
            }
            if (best != null) {
                best.cuts.add(bestT);
                snapLinks.add(new double[] {endpoint[0], endpoint[1], best.x(bestT), best.z(bestT)});
            }
        }

        Map<Long, Integer> nodeIndex = new HashMap<>();
        List<double[]> nodes = new ArrayList<>();
        List<int[]> edges = new ArrayList<>();
        List<Double> factors = new ArrayList<>();
        for (Segment segment : segments) {
            int previous = -1;
            for (double t : segment.cuts) {
                int node = node(nodeIndex, nodes, segment.x(t), segment.z(t));
                if (previous >= 0 && previous != node) {
                    edges.add(new int[] {previous, node});
                    factors.add(segment.factor);
                }
                previous = node;
            }
        }
        for (double[] link : snapLinks) {
            int a = node(nodeIndex, nodes, link[0], link[1]);
            int b = node(nodeIndex, nodes, link[2], link[3]);
            if (a != b) {
                edges.add(new int[] {a, b});
                factors.add(1.0D);
            }
        }

        int count = nodes.size();
        double[] nodeX = new double[count];
        double[] nodeZ = new double[count];
        for (int index = 0; index < count; index++) {
            nodeX[index] = nodes.get(index)[0];
            nodeZ[index] = nodes.get(index)[1];
        }
        List<List<int[]>> adjacency = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            adjacency.add(new ArrayList<>());
        }
        int[] edgeA = new int[edges.size()];
        int[] edgeB = new int[edges.size()];
        double[] edgeFactor = new double[edges.size()];
        for (int index = 0; index < edges.size(); index++) {
            int[] edge = edges.get(index);
            edgeA[index] = edge[0];
            edgeB[index] = edge[1];
            edgeFactor[index] = factors.get(index);
            adjacency.get(edge[0]).add(new int[] {edge[1], index});
            adjacency.get(edge[1]).add(new int[] {edge[0], index});
        }
        int[][] neighbors = new int[count][];
        double[][] costs = new double[count][];
        for (int index = 0; index < count; index++) {
            List<int[]> list = adjacency.get(index);
            neighbors[index] = new int[list.size()];
            costs[index] = new double[list.size()];
            for (int k = 0; k < list.size(); k++) {
                int other = list.get(k)[0];
                int edge = list.get(k)[1];
                neighbors[index][k] = other;
                costs[index][k] = Math.hypot(nodeX[other] - nodeX[index], nodeZ[other] - nodeZ[index]) * edgeFactor[edge];
            }
        }
        return new RoadGraph(nodeX, nodeZ, neighbors, costs, edgeA, edgeB, edgeFactor);
    }

    public boolean isEmpty() {
        return edgeA.length == 0;
    }

    private static int node(Map<Long, Integer> index, List<double[]> nodes, double x, double z) {
        long key = (((long) Math.round(x / MERGE)) << 32) ^ (Math.round(z / MERGE) & 0xFFFFFFFFL);
        Integer existing = index.get(key);
        if (existing != null) {
            return existing;
        }
        nodes.add(new double[] {x, z});
        index.put(key, nodes.size() - 1);
        return nodes.size() - 1;
    }

    private static void intersect(Segment first, Segment second) {
        if (Math.max(first.ax, first.bx) < Math.min(second.ax, second.bx)
                || Math.max(second.ax, second.bx) < Math.min(first.ax, first.bx)
                || Math.max(first.az, first.bz) < Math.min(second.az, second.bz)
                || Math.max(second.az, second.bz) < Math.min(first.az, first.bz)) {
            return;
        }
        double rx = first.bx - first.ax;
        double rz = first.bz - first.az;
        double sx = second.bx - second.ax;
        double sz = second.bz - second.az;
        double denominator = rx * sz - rz * sx;
        if (Math.abs(denominator) < 1.0E-9D) {
            return;
        }
        double qx = second.ax - first.ax;
        double qz = second.az - first.az;
        double t = (qx * sz - qz * sx) / denominator;
        double u = (qx * rz - qz * rx) / denominator;
        if (t >= -1.0E-6D && t <= 1.0D + 1.0E-6D && u >= -1.0E-6D && u <= 1.0D + 1.0E-6D) {
            first.cuts.add(Math.max(0.0D, Math.min(1.0D, t)));
            second.cuts.add(Math.max(0.0D, Math.min(1.0D, u)));
        }
    }

    private static double project(Segment segment, double x, double z) {
        double dx = segment.bx - segment.ax;
        double dz = segment.bz - segment.az;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 1.0E-9D) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, ((x - segment.ax) * dx + (z - segment.az) * dz) / lengthSquared));
    }

    private record Access(int edge, double t, double x, double z, double distance) {
    }

    private Access nearest(double x, double z) {
        Access best = null;
        for (int edge = 0; edge < edgeA.length; edge++) {
            double ax = nodeX[edgeA[edge]];
            double az = nodeZ[edgeA[edge]];
            double dx = nodeX[edgeB[edge]] - ax;
            double dz = nodeZ[edgeB[edge]] - az;
            double lengthSquared = dx * dx + dz * dz;
            double t = lengthSquared < 1.0E-9D ? 0.0D
                    : Math.max(0.0D, Math.min(1.0D, ((x - ax) * dx + (z - az) * dz) / lengthSquared));
            double px = ax + dx * t;
            double pz = az + dz * t;
            double distance = Math.hypot(px - x, pz - z);
            if (best == null || distance < best.distance()) {
                best = new Access(edge, t, px, pz, distance);
            }
        }
        return best;
    }

    public List<double[]> route(double sx, double sz, double tx, double tz) {
        List<double[]> straight = new ArrayList<>(List.of(new double[] {sx, sz}, new double[] {tx, tz}));
        if (edgeA.length == 0) {
            return straight;
        }
        Access start = nearest(sx, sz);
        Access goal = nearest(tx, tz);
        if (start == null || goal == null || start.distance() > MAX_ACCESS || goal.distance() > MAX_ACCESS) {
            return straight;
        }

        int count = nodeX.length;
        int startNode = count;
        int goalNode = count + 1;
        double[] best = new double[count + 2];
        int[] previous = new int[count + 2];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        Arrays.fill(previous, -1);
        best[startNode] = 0.0D;
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        queue.add(new double[] {heuristic(start.x(), start.z(), goal), startNode});
        while (!queue.isEmpty()) {
            double[] item = queue.poll();
            int current = (int) item[1];
            if (current == goalNode) {
                break;
            }
            double base = best[current];
            if (item[0] - heuristic(x(current, start, goal), z(current, start, goal), goal) > base + 1.0E-6D) {
                continue;
            }
            if (current == startNode) {
                relax(queue, best, previous, current, edgeA[start.edge()], partial(start, true), goal, start);
                relax(queue, best, previous, current, edgeB[start.edge()], partial(start, false), goal, start);
                if (start.edge() == goal.edge()) {
                    double direct = Math.abs(goal.t() - start.t()) * edgeLength(start.edge()) * edgeFactor[start.edge()];
                    relax(queue, best, previous, current, goalNode, direct, goal, start);
                }
                continue;
            }
            if (current < count) {
                for (int k = 0; k < neighbors[current].length; k++) {
                    relax(queue, best, previous, current, neighbors[current][k], costs[current][k], goal, start);
                }
                if (current == edgeA[goal.edge()]) {
                    relax(queue, best, previous, current, goalNode, partial(goal, true), goal, start);
                }
                if (current == edgeB[goal.edge()]) {
                    relax(queue, best, previous, current, goalNode, partial(goal, false), goal, start);
                }
            }
        }
        if (Double.isInfinite(best[goalNode])) {
            return straight;
        }

        List<double[]> reversed = new ArrayList<>();
        for (int node = previous[goalNode]; node >= 0 && node != startNode; node = previous[node]) {
            reversed.add(new double[] {nodeX[node], nodeZ[node]});
        }
        List<double[]> path = new ArrayList<>();
        path.add(new double[] {sx, sz});
        append(path, start.x(), start.z());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            append(path, reversed.get(index)[0], reversed.get(index)[1]);
        }
        append(path, goal.x(), goal.z());
        append(path, tx, tz);
        return path;
    }

    private double x(int node, Access start, Access goal) {
        return node == nodeX.length ? start.x() : node == nodeX.length + 1 ? goal.x() : nodeX[node];
    }

    private double z(int node, Access start, Access goal) {
        return node == nodeX.length ? start.z() : node == nodeX.length + 1 ? goal.z() : nodeZ[node];
    }

    private double edgeLength(int edge) {
        return Math.hypot(nodeX[edgeB[edge]] - nodeX[edgeA[edge]], nodeZ[edgeB[edge]] - nodeZ[edgeA[edge]]);
    }

    private double partial(Access access, boolean towardA) {
        double fraction = towardA ? access.t() : 1.0D - access.t();
        return fraction * edgeLength(access.edge()) * edgeFactor[access.edge()];
    }

    private static double heuristic(double x, double z, Access goal) {
        return Math.hypot(goal.x() - x, goal.z() - z) * 0.85D;
    }

    private void relax(PriorityQueue<double[]> queue, double[] best, int[] previous, int from, int to, double cost,
                       Access goal, Access start) {
        double candidate = best[from] + cost;
        if (candidate + 1.0E-9D < best[to]) {
            best[to] = candidate;
            previous[to] = from;
            queue.add(new double[] {candidate + heuristic(x(to, start, goal), z(to, start, goal), goal), to});
        }
    }

    private static void append(List<double[]> path, double x, double z) {
        double[] last = path.get(path.size() - 1);
        if (Math.hypot(last[0] - x, last[1] - z) > 0.5D) {
            path.add(new double[] {x, z});
        }
    }

    public static double length(List<double[]> path, int fromIndex) {
        double total = 0.0D;
        for (int index = Math.max(1, fromIndex + 1); index < path.size(); index++) {
            total += Math.hypot(path.get(index)[0] - path.get(index - 1)[0], path.get(index)[1] - path.get(index - 1)[1]);
        }
        return total;
    }
}

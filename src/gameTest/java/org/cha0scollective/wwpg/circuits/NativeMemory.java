package org.cha0scollective.wwpg.circuits;
import java.util.*;
import java.nio.file.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtIo;
import org.patryk3211.powergrid.circuits.schematic.*;
import org.patryk3211.powergrid.circuits.components.*;

/** Native signed-data memory cell: a write relay gates a polarized storage relay. */
final class NativeMemory {
    static CircuitSchematic cell(GameTestHelper h) {
        var base=new CircuitSchematic();base.setName("WWPG native nonvolatile bit cell");
        var cs=new ArrayList<PlacedComponent>();
        int[][] coords={{0,0},{5,0},{10,0},{0,12},{10,12},{2,4},{8,4}};
        String[] labels={"GND","VCC","DATA","WRITE","Q"};
        for(int i=0;i<7;i++){
            var c=new PlacedComponent(i<5?Components.CONNECTOR.get():Components.RELAY.get(),coords[i][0],coords[i][1],null);
            if(i<5)c.setString(Component.LABEL,labels[i]);
            else {c.set(RelayComponent.THRESHOLD_VOLTAGE,6f);c.set(RelayComponent.POLARIZED,i==6);c.set(RelayComponent.STATE,false);}
            base.placeComponent(c,c.x,c.y);cs.add(c);
        }
        // component index / pad; relay 0/1 coil, 2 NC, 3 common, 4 NO.
        int[][][] nets={{{0,0},{5,1},{5,2},{6,1},{6,2}},{{1,0},{6,4}},{{2,0},{5,4}},{{3,0},{5,0}},{{4,0},{6,3}},{{5,3},{6,0}}};
        var routes=new ArrayList<Point[]>();
        for(var net:nets)for(int j=1;j<net.length;j++)routes.add(new Point[]{pad(cs.get(net[j-1][0]),net[j-1][1]),pad(cs.get(net[j][0]),net[j][1])});
        var rng=new Random(1855);
        for(int attempt=0;attempt<300;attempt++){
            var s=new CircuitSchematic(base);var router=new Router(s);
            try{for(var route:routes)router.route(route[0],route[1]);
                Path out=Path.of("rebuilt");Files.createDirectories(out);try(var stream=Files.newOutputStream(out.resolve("native-memory-cell.nbt"))){NbtIo.writeCompressed(s.serializeNbt(h.getLevel().registryAccess()),stream);}return s;
            }catch(IllegalStateException blocked){Collections.shuffle(routes,rng);}catch(java.io.IOException e){throw new RuntimeException(e);}
        }throw new IllegalStateException("Cannot route native memory cell");
    }
    static Point pad(PlacedComponent c,int node){return c.footprint().getPads().entrySet().stream().filter(e->e.getValue().nodeIndex()==node).map(e->new Point(e.getKey().x()+c.x,e.getKey().y()+c.y)).findFirst().orElseThrow();}
    // Adapted from the repository's MIT-licensed BoardFixture.Router.
    static final class Router {
        final CircuitSchematic s;final Set<Point> pads=new HashSet<>(),bodies=new HashSet<>();final boolean[][][] occupied=new boolean[2][16][16];
        Router(CircuitSchematic s){this.s=s;for(var c:s.components()){c.footprint().getPads().keySet().forEach(p->pads.add(new Point(p.x()+c.x,p.y()+c.y)));for(int x=c.x;x<c.x+c.footprint().getWidth();x++)for(int y=c.y;y<c.y+c.footprint().getHeight();y++)bodies.add(new Point(x,y));}}
        void route(Point from,Point to){var q=new ArrayDeque<Cell>();var previous=new HashMap<Cell,Cell>();for(int l=0;l<2;l++){var a=new Cell(l,from.x(),from.y());q.add(a);previous.put(a,null);}Cell end=null;
            while(!q.isEmpty()){var a=q.removeFirst();if(a.x==to.x()&&a.y==to.y()){end=a;break;}var nexts=new ArrayList<Cell>();for(var d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})nexts.add(new Cell(a.l,a.x+d[0],a.y+d[1]));if(!bodies.contains(new Point(a.x,a.y)))nexts.add(new Cell(1-a.l,a.x,a.y));for(var n:nexts){if(n.x<0||n.x>15||n.y<0||n.y>15||previous.containsKey(n))continue;var p=new Point(n.x,n.y);if(occupied[n.l][n.x][n.y]&&!p.equals(to)||pads.contains(p)&&!p.equals(from)&&!p.equals(to))continue;previous.put(n,a);q.addLast(n);}}
            if(end==null)throw new IllegalStateException("route blocked");var path=new ArrayList<Cell>();for(var a=end;a!=null;a=previous.get(a))path.add(a);Collections.reverse(path);for(int i=0;i<path.size();i++){var a=path.get(i);occupied[a.l][a.x][a.y]=true;if(i==0)continue;var b=path.get(i-1);if(a.l!=b.l){s.placeComponent(new PlacedComponent(Components.VIA.get(),a.x,a.y,null),a.x,a.y);pads.add(new Point(a.x,a.y));}else{var layer=a.l==0?s.front():s.back();if(a.x==b.x)layer.addVerticalLine(a.x,Math.min(a.y,b.y),Math.max(a.y,b.y));else layer.addHorizontalLine(a.y,Math.min(a.x,b.x),Math.max(a.x,b.x));}}
        }
        record Cell(int l,int x,int y){}
    }
}

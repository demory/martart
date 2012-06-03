package org.rhok.marta;

import java.util.List;
import java.awt.geom.Point2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.ShapePoint;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.serialization.GtfsReader;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedHeader.Incrementality;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.google.transit.realtime.GtfsRealtimeConstants;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( final String[] args )
    {
    	
    	GtfsReader reader = new GtfsReader();
        try {
			reader.setInputLocation(new File(args[0]));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

        /**
         * You can register an entity handler that listens for new objects as they
         * are read
         */
        //reader.addEntityHandler(new GtfsEntityHandler());

        GtfsDaoImpl store = new GtfsDaoImpl();
        reader.setEntityStore(store);

        try {
			reader.run();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	Calendar calendar = Calendar.getInstance();
    	int dow = calendar.get(Calendar.DAY_OF_WEEK);
    	
    	System.out.println("dow "+dow);
    	
    	ServiceCalendar currentCal = null;
    	for (ServiceCalendar cal : store.getAllCalendars()) {
    		switch (dow) {
				case 1: 
					if(cal.getSunday() > 0) currentCal = cal;
					break;
				case 2: 
					if(cal.getMonday() > 0) currentCal = cal;
					break;
				case 3: 
					if(cal.getTuesday() > 0) currentCal = cal;
					break;
				case 4: 
					if(cal.getWednesday() > 0) currentCal = cal;
					break;
				case 5: 
					if(cal.getThursday() > 0) currentCal = cal;
					break;
				case 6: 
					if(cal.getFriday() > 0) currentCal = cal;
					break;
				case 7: 
					if(cal.getSaturday() > 0) currentCal = cal;
					break;

				default:
					break;
			}
        }
		System.out.println("ccal=" + currentCal.getServiceId());
        
        Set<Route> rtes = new HashSet<Route>();
        // Access entities through the store
        for (Route route : store.getAllRoutes()) {
        	if(route.getType() == 1) {
                System.out.println("route: " + route.getShortName());
                rtes.add(route);
        	}
        }
        
        // find shapeIds of interest
        Set<AgencyAndId> shapeIDs = new HashSet<AgencyAndId>();
        final Map<AgencyAndId, TripRange> tripMap = new HashMap<AgencyAndId, TripRange>();
        for (Trip trip : store.getAllTrips()) {
        	if(rtes.contains(trip.getRoute()) && trip.getServiceId().equals(currentCal.getServiceId())) {
        		//System.out.println("trip: "+trip + " sid="+trip.getServiceId());
        		tripMap.put(trip.getId(), new TripRange(trip));
        		shapeIDs.add(trip.getShapeId());
        	}
        }
        
        // process shape pts
        final Map<AgencyAndId, ShapeSequence>  shapePts = new HashMap<AgencyAndId, ShapeSequence>();
        for(ShapePoint sp : store.getAllShapePoints()) {
        	if(shapeIDs.contains(sp.getShapeId())) {
        		ShapeSequence shpSeq = null;
        		if(shapePts.containsKey(sp.getShapeId())) {
        			shpSeq = shapePts.get(sp.getShapeId());
        		}
        		else {
        			shpSeq = new ShapeSequence();
        			shapePts.put(sp.getShapeId(), shpSeq);
        		}
        		shpSeq.addPoint(sp.getSequence(), new Point2D.Double(sp.getLon(), sp.getLat()));
        	}
        }
        
        for(ShapeSequence seq : shapePts.values()) {
        	seq.createList();
        }
        
        // process stop times
        for(StopTime time : store.getAllStopTimes()) {
        	if(tripMap.containsKey(time.getTrip().getId())) {
        		//System.out.println(time.getArrivalTime() +" "+time.getDepartureTime());
        		int timeVal  = time.getArrivalTime() + time.getDepartureTime() / 2;
        		tripMap.get(time.getTrip().getId()).addTime(timeVal, time.getStop());
        	}
        }
        
        //////////////
        
        
        /*for(TripRange tr : tripMap.values()) {
        	if(tr.containsTime(curTime)) {
        		System.out.println(tr.trip);
        		ShapeSequence shpSeq = shapePts.get(tr.trip.getShapeId());
        		tr.getLocation(curTime, shpSeq);
        	}
        }*/
                
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				
				Calendar calendar = Calendar.getInstance();
		    		
				int curTime = calendar.get(Calendar.HOUR_OF_DAY)*3600 + calendar.get(Calendar.MINUTE)*60 + calendar.get(Calendar.SECOND);
				System.out.println("curtime="+curTime);
		                
				FeedMessage.Builder feedMessageBuilder = FeedMessage.newBuilder();
		        
		        FeedHeader.Builder header = FeedHeader.newBuilder();
		        header.setTimestamp(System.currentTimeMillis());
		        header.setIncrementality(Incrementality.FULL_DATASET);
		        header.setGtfsRealtimeVersion(GtfsRealtimeConstants.VERSION);
		        feedMessageBuilder.setHeader(header);

		        int i = 0;
		        for(TripRange tr : tripMap.values()) {
		        	if(tr.containsTime(curTime)) {
		        		System.out.println(tr.trip);
		        		ShapeSequence shpSeq = shapePts.get(tr.trip.getShapeId());
		        		Point2D loc = tr.getLocation(curTime, shpSeq);
		        		
		                VehiclePosition.Builder vp = VehiclePosition.newBuilder();

		                TripDescriptor.Builder td = TripDescriptor.newBuilder();
		                td.setTripId(tr.trip.getId().toString());
		                td.setRouteId(tr.trip.getRoute().getId().toString());
		                vp.setTrip(td);

		                VehicleDescriptor.Builder vd = VehicleDescriptor.newBuilder();
		                vd.setId("vehicle"+i);
		                vp.setVehicle(vd);

		                vp.setTimestamp(System.currentTimeMillis());

		                Position.Builder position = Position.newBuilder();
		                position.setLatitude((float) loc.getY());
		                position.setLongitude((float) loc.getX());
		                vp.setPosition(position);

		                FeedEntity.Builder entity = FeedEntity.newBuilder();
		                entity.setId("entity"+i);
		                entity.setVehicle(vp);
		                feedMessageBuilder.addEntity(entity);
		                
		                i++;
		        	}
		        }
		        
		        FeedMessage message = feedMessageBuilder.build();

		        BufferedOutputStream out;
				try {
					out = new BufferedOutputStream(new FileOutputStream(args[1]));
			        message.writeTo(out);
			        out.close();
			    } catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, 0, Integer.parseInt(args[2]));
                
        
        /*FeedMessage.Builder feedMessageBuilder = FeedMessage.newBuilder();
        
        FeedHeader.Builder header = FeedHeader.newBuilder();
        header.setTimestamp(System.currentTimeMillis());
        header.setIncrementality(Incrementality.FULL_DATASET);
        header.setGtfsRealtimeVersion(GtfsRealtimeConstants.VERSION);
        feedMessageBuilder.setHeader(header);

        VehiclePosition.Builder vp = VehiclePosition.newBuilder();

        TripDescriptor.Builder td = TripDescriptor.newBuilder();
        td.setTripId("tripId");
        td.setRouteId("BLUE");
        vp.setTrip(td);

        VehicleDescriptor.Builder vd = VehicleDescriptor.newBuilder();
        vd.setId("vehicleId");
        vp.setVehicle(vd);

        vp.setTimestamp(System.currentTimeMillis());

        Position.Builder position = Position.newBuilder();
        position.setLatitude((float) 47.653738);
        position.setLongitude((float) -122.307786);
        vp.setPosition(position);

        FeedEntity.Builder entity = FeedEntity.newBuilder();
        entity.setId("entityId");
        entity.setVehicle(vp);
        feedMessageBuilder.addEntity(entity);

        FeedMessage message = feedMessageBuilder.build();

        BufferedOutputStream out;
		try {
			out = new BufferedOutputStream(new FileOutputStream("/home/demory/rhok/marta/feed-test"));
	        message.writeTo(out);
	        out.close();
	    } catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/

        
        //System.out.println( "finished" );
    }
    
    public static class TripRange {
    	Trip trip;
    	SortedMap<Integer, Stop> times = new TreeMap<Integer, Stop>();
    	
    	public TripRange(Trip trip) {
    		this.trip = trip;
    	}
    	
    	public void addTime(int time, Stop stop) {
    		times.put(time, stop);
    	}
    	
    	public boolean containsTime(int time) {
    		return time >= times.firstKey() && time <= times.lastKey();
    	}
    	
    	public Point2D getLocation(int time, ShapeSequence shpSeq) {
    		List<Integer> timeList = new ArrayList<Integer>(times.keySet());
    		
    		for(int i = 0; i < timeList.size()-1; i++) {
    			if(time >= timeList.get(i) && time <= timeList.get(i+1)) {
    				//System.out.println(" tl: "+timeList.get(i)+" to "+timeList.get(i+1));
    				Stop stop1 = times.get(timeList.get(i));
    				Stop stop2 = times.get(timeList.get(i+1));
    				System.out.println(" between "+stop1.getName() + " and "+stop2.getName());
    				int i1 = shpSeq.getClosest(new Point2D.Double(stop1.getLon(), stop1.getLat()));
    				int i2 = shpSeq.getClosest(new Point2D.Double(stop2.getLon(), stop2.getLat()));
    				double pct = (double) (time-timeList.get(i)) / (timeList.get(i+1)-timeList.get(i));
    				//System.out.println(" time="+time+" pct="+pct);
    				Point2D pt = shpSeq.ptBetween(i1, i2, pct);
    				System.out.println(" "+pt);
    				return pt;
    			}
    		}
    		return null;
    		
    	}
    }
    
    public static class ShapeSequence {
    	SortedMap<Integer, Point2D> ptMap = new TreeMap<Integer, Point2D>();
    	List<Point2D> ptList;
    	
    	public void addPoint(int seq, Point2D pt) {
    		ptMap.put(seq, pt);
    	}
    	
    	public void createList() {
    		ptList = new ArrayList<Point2D>(ptMap.values());
    	}
    	
    	public int getClosest(Point2D test) {
    		double bestDist = Double.MAX_VALUE;
    		int bestI = 0;
    		for(int i =0; i < ptList.size(); i++) {
    			Point2D pt = ptList.get(i);
    			double dist = test.distance(pt);
    			if(dist < bestDist) {
    				bestDist = dist;
    				bestI = i;
    			}
    		}
    		return bestI;
    	}
    	
    	public Point2D ptBetween(int i1, int i2, double pct) {
    		double totalDist = 0;
    		//System.out.println("pct="+pct);
    		for(int i=i1; i<i2-1; i++) {
    			totalDist += ptList.get(i).distance(ptList.get(i+1));    			
    		}
    		double targetDist = pct*totalDist;
    		double distCovered = 0;
    		for(int i=i1; i<i2-1; i++) {
    			double segDist = ptList.get(i).distance(ptList.get(i+1));
    			if(targetDist >= distCovered && targetDist <= distCovered+segDist) {
    				double pctAlongSeg = (targetDist - distCovered) / segDist;
    				double x1 = ptList.get(i).getX(), y1 = ptList.get(i).getY(); 
    				double x2 = ptList.get(i+1).getX(), y2 = ptList.get(i+1).getY();
    				double x = x1 + pctAlongSeg * (x2-x1);
    				double y = y1 + pctAlongSeg * (y2-y1);
    				return new Point2D.Double(x ,y);
    			}
    			distCovered += segDist;
    		}
    		return null;
    	}
    }
}

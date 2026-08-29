"""Coordinate conversion required by mainland-China providers."""
import math

def _outside_china(lat, lon): return not (0.8293 <= lon <= 137.8347 and 0.8293 <= lat <= 55.8271)
def _transform_lat(x,y): return -100+2*x+3*y+.2*y*y+.1*x*y+.2*math.sqrt(abs(x))+(20*math.sin(6*x*math.pi)+20*math.sin(2*x*math.pi))*2/3+(20*math.sin(y*math.pi)+40*math.sin(y/3*math.pi))*2/3+(160*math.sin(y/12*math.pi)+320*math.sin(y*math.pi/30))*2/3
def _transform_lon(x,y): return 300+x+2*y+.1*x*x+.1*x*y+.1*math.sqrt(abs(x))+(20*math.sin(6*x*math.pi)+20*math.sin(2*x*math.pi))*2/3+(20*math.sin(x*math.pi)+40*math.sin(x/3*math.pi))*2/3+(150*math.sin(x/12*math.pi)+300*math.sin(x/30*math.pi))*2/3
def wgs84_to_gcj02(lat,lon):
    lat,lon=float(lat),float(lon)
    if _outside_china(lat,lon): return lat,lon
    a,ee=6378245.0,0.006693421622965943
    dlat,dlon=_transform_lat(lon-105,lat-35),_transform_lon(lon-105,lat-35)
    rad=lat/180*math.pi; magic=1-ee*math.sin(rad)**2; sqrt=math.sqrt(magic)
    dlat=(dlat*180)/((a*(1-ee))/(magic*sqrt)*math.pi); dlon=(dlon*180)/(a/sqrt*math.cos(rad)*math.pi)
    return lat+dlat,lon+dlon

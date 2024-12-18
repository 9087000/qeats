
/*
 * Copyright (c) Crio.Do 2019. All rights reserved
 */

package com.crio.qeats.utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import javax.validation.constraints.NotNull;

/**
 * Utility class to hold latitude and longitude.
 */
public class GeoLocation implements Serializable {

  @NotNull private Double latitude;
  @NotNull private Double longitude;

  public GeoLocation() {}

  public GeoLocation(Double latitude, Double longitude) {
    this.latitude = latitude;
    this.longitude = longitude;
  }

  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder();
    if (getLatitude() != null) {
      stringBuilder.append(getLatitude()).append(" ");
    }
    if (getLongitude() != null) {
      stringBuilder.append(getLongitude());
    }
    return stringBuilder.toString();
  }

  public boolean equals(Object obj) {
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    GeoLocation geoLocationB = (GeoLocation) obj;
    if (getLatitude().equals(geoLocationB.getLatitude())
        && getLongitude().equals(geoLocationB.getLongitude())) {
      return true;
    } else {
      return false;
    }
  }

  public int hashCode() {
    return super.hashCode();
  }

  @JsonIgnore
  public boolean isValidGeoLocation() {
    if (getLatitude() != null
        && getLatitude() >= -90.0D
        && getLatitude() <= 90.0D
        && getLongitude() != null
        && getLongitude() >= -180.0D
        && getLongitude() <= 180.0D) {
      return true;
    }
    return false;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public static double distanceInKm(Double latitude1, Double longitude1, Double latitude2, Double longitude2) {
    int earthRadiusKm = 6371;
    double dLat = Math.toRadians(latitude2 - latitude1);
    double dLon = Math.toRadians(longitude2 - longitude1);
    double lat1 = Math.toRadians(latitude1);
    double lat2 = Math.toRadians(latitude2);

    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return earthRadiusKm * c;
}


}

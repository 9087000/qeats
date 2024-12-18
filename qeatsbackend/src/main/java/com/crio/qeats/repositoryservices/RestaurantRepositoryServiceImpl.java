/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.repositoryservices;

import ch.hsr.geohash.GeoHash;
import com.crio.qeats.configs.RedisConfiguration;
import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.globals.GlobalConstants;
import com.crio.qeats.models.ItemEntity;
import com.crio.qeats.models.MenuEntity;
import com.crio.qeats.models.RestaurantEntity;
import com.crio.qeats.repositories.ItemRepository;
import com.crio.qeats.repositories.MenuRepository;
import com.crio.qeats.repositories.RestaurantRepository;
import com.crio.qeats.utils.GeoLocation;
import com.crio.qeats.utils.GeoUtils;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOError;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Provider;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;

@Service
public class RestaurantRepositoryServiceImpl implements RestaurantRepositoryService {

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private RedisConfiguration redisConfiguration;

  @Autowired
  private RestaurantRepository restaurantRepository;

  @Autowired
  private Provider<ModelMapper> modelMapperProvider;

  private boolean isOpenNow(LocalTime time, RestaurantEntity res) {
    LocalTime openingTime = LocalTime.parse(res.getOpensAt());
    LocalTime closingTime = LocalTime.parse(res.getClosesAt());

    return time.isAfter(openingTime) && time.isBefore(closingTime);
  }

  @Override
  public List<Restaurant> findAllRestaurantsCloseBy(Double latitude,
      Double longitude, LocalTime currentTime, Double servingRadiusInKms) {

    List<Restaurant> restaurantList = null;
    if (redisConfiguration.isCacheAvailable()) {
      restaurantList = findAllRestaurantsCloseFromCache(
          latitude, longitude, currentTime, servingRadiusInKms);
    } else {
      restaurantList = findAllRestaurantsCloseFromDb(
          latitude, longitude, currentTime, servingRadiusInKms);
    }
    return restaurantList;
  }

  // TODO: CRIO_TASK_MODULE_NOSQL
  // Objectives:
  // 1. Implement findAllRestaurantsCloseby.
  // 2. Remember to keep the precision of GeoHash in mind while using it as a key.
  // Check RestaurantRepositoryService.java file for the interface contract.

  //@Override
  public List<Restaurant> findAllRestaurantsCloseFromDb(Double latitude,
      Double longitude, LocalTime currentTime, Double servingRadiusInKms) {

    ModelMapper modelMapper = modelMapperProvider.get();

    List<RestaurantEntity> restaurantEntityList = restaurantRepository.findAll();
    List<Restaurant> restaurantList = new ArrayList<>();

    for (RestaurantEntity restaurantEntity : restaurantEntityList) {
      if (isOpenNow(currentTime, restaurantEntity)) {
        if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime,
            latitude, longitude, servingRadiusInKms)) {

          // System.out.println(restaurantEntity.getRestaurantId());
          restaurantList.add(modelMapper.map(restaurantEntity, Restaurant.class));
          // System.out.println(restaurantList);
        }
      }

    }
    // System.out.println(restaurantList);
    //CHECKSTYLE:OFF
    //CHECKSTYLE:ON
    // System.out.println(restaurantList.size());
    return restaurantList;
  }

  public List<Restaurant> findAllRestaurantsCloseFromCache(Double latitude, 
      Double longitude, LocalTime currentTime, Double servingRadiusInKms) {

    redisConfiguration.setRedisPort(6379);
    List<Restaurant> restaurantList = new ArrayList<>();
    ObjectMapper objectMapper = new ObjectMapper();
    ModelMapper modelMapper = modelMapperProvider.get();
    // TODO: CRIO_TASK_MODULE_REDIS
    // We want to use cache to speed things up. Write methods that
    // perform the same functionality,
    // but using the cache if it is present and reachable.
    // Remember, you must ensure that if cache is not present, the queries are
    // directed at the
    // database instead.
    GeoHash geoHash = GeoHash.withCharacterPrecision(latitude, longitude, 7);
    String geoHashKey = geoHash.toBase32();

    try (Jedis jedis = redisConfiguration.getJedisPool().getResource()) {

      // get value for above GeoHash string
      String geoHashValue = jedis.get(geoHashKey);

      //List<RestaurantEntity> restaurantEntityList = new ArrayList<>();
      if (geoHashValue != null) {
        try {
          restaurantList = objectMapper.readValue(
              geoHashValue, new TypeReference<List<Restaurant>>() {
              });
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else {
        try {
          restaurantList = findAllRestaurantsCloseFromDb(
              latitude, longitude, currentTime, servingRadiusInKms);
          geoHashValue = objectMapper.writeValueAsString(restaurantList);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        jedis.set(geoHashKey, geoHashValue);
      }
      return restaurantList;
    }
    
    
  }
  //catch (JedisConnectionException e) {
  //   restaurantList = findAllRestaurantsCloseByMongo(
  //       latitude, longitude, currentTime, servingRadiusInKms);
  //   return restaurantList;
  // } catch (JsonParseException e) {
  //   // TODO Auto-generated catch block
  //   e.printStackTrace();
  // } catch (JsonMappingException e) {
  //   // TODO Auto-generated catch block
  //   e.printStackTrace();
  // } catch (IOException e) {
  //   // TODO Auto-generated catch block
  //   e.printStackTrace();
  // } catch (RuntimeException e) {
  //   System.out.println(e.getMessage());
  //   return null;
  // }
    



  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose names have an exact or partial match with the search query.
  @Override
  public List<Restaurant> findRestaurantsByName(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

    ModelMapper modelMapper = modelMapperProvider.get();
    
    Optional<List<RestaurantEntity>> restaurantEntityListExactOptional = 
        restaurantRepository.findRestaurantsByNameExact(searchString);

    List<RestaurantEntity> restaurantEntityListExact = new ArrayList<>();
    if (restaurantEntityListExactOptional.isPresent()) {
      restaurantEntityListExact = restaurantEntityListExactOptional.get();
    }
    
    List<RestaurantEntity> restaurantEntityListPartial = 
        restaurantRepository.findRestaurantsByNamePartial(searchString);

    //create set to store unique values in insertion order 
    Set<RestaurantEntity> set = new LinkedHashSet<>();
    //add the elements of restaurantEntityListExact to the set 
    for (RestaurantEntity restaurantEntity : restaurantEntityListExact) {
      set.add(restaurantEntity);
    }

    //add the elements of restaurantEntityListPartial to the set 
    for (RestaurantEntity restaurantEntity : restaurantEntityListPartial) {
      set.add(restaurantEntity);
    }

    //create an aggregated list of RestaurantEntity from the set 
    List<RestaurantEntity> restaurantEntityList = new ArrayList<>(set);

    //create a new Restaurant list to return 
    List<Restaurant> restaurantList = new ArrayList<>();

    //check if the restaurantEntityList are close by & open, if so add them to restaurantList
    for (RestaurantEntity restaurantEntity : restaurantEntityList) {
      if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime,
          latitude, longitude, servingRadiusInKms)) {

        restaurantList.add(modelMapper.map(restaurantEntity, Restaurant.class));
      }

    }
    return restaurantList;
  }


  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose attributes (cuisines) intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByAttributes(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

    ModelMapper modelMapper = modelMapperProvider.get();
    List<RestaurantEntity> restaurantEntityList = 
          restaurantRepository.findRestaurantsByAttributes(searchString);

    //create Restaurant list to return 
    List<Restaurant> restaurantList = new ArrayList<>();

    //check if the restaurantEntityList are close by & open, if so add them to restaurantList
    for (RestaurantEntity restaurantEntity : restaurantEntityList) {
      if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime,
          latitude, longitude, servingRadiusInKms)) {

        restaurantList.add(modelMapper.map(restaurantEntity, Restaurant.class));
      }

    }
    return restaurantList;
  }



  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose names form a complete or partial match
  // with the search query.

  @Override
  public List<Restaurant> findRestaurantsByItemName(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    
    // List<ItemEntity> itemEntityList = 
    //     ItemRepository.findByName(searchString);

    return null;

  }

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose attributes intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByItemAttributes(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

    return null;
  }





  /**
   * Utility method to check if a restaurant is within the serving radius at a given time.
   * @return boolean True if restaurant falls within serving radius and is open, false otherwise
   */
  private boolean isRestaurantCloseByAndOpen(RestaurantEntity restaurantEntity,
      LocalTime currentTime, Double latitude, Double longitude, Double servingRadiusInKms) {
    if (isOpenNow(currentTime, restaurantEntity)) {
      return GeoUtils.findDistanceInKm(latitude, longitude,
          restaurantEntity.getLatitude(), restaurantEntity.getLongitude())
          < servingRadiusInKms;
    }

    return false;
  }



}

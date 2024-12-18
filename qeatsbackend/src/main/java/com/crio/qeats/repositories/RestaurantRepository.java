/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.repositories;

import com.crio.qeats.models.RestaurantEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface RestaurantRepository extends MongoRepository<RestaurantEntity, String> {

  @Query("{name: ?0}")
  Optional<List<RestaurantEntity>> findRestaurantsByNameExact(String searchString);

  @Query("{name: {$regex: ?0}}")
  List<RestaurantEntity> findRestaurantsByNamePartial(String searchString);

  @Query("{attributes: {$regex: ?0}}")
  List<RestaurantEntity> findRestaurantsByAttributes(String searchString);

}

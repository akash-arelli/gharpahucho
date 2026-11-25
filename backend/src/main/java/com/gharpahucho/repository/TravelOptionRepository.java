package com.gharpahucho.repository;

import com.gharpahucho.entity.TravelOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TravelOptionRepository extends JpaRepository<TravelOption, UUID> {
    List<TravelOption> findByOriginCityAndDestinationCityAndActiveTrue(String originCity, String destinationCity);
}

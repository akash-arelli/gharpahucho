package com.gharpahucho.controller;

import com.gharpahucho.dto.PlanDtos.CityDto;
import com.gharpahucho.integration.maps.City;
import com.gharpahucho.integration.maps.CityGazetteer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maps")
@RequiredArgsConstructor
public class MapController {

    private final CityGazetteer gazetteer;

    /** City autocomplete (English, Hindi and Telugu names). Public: the landing page uses it too. */
    @GetMapping("/cities")
    public List<CityDto> cities(@RequestParam(required = false, defaultValue = "") String q,
                                @RequestParam(defaultValue = "8") int limit) {
        return gazetteer.search(q, Math.min(40, Math.max(1, limit))).stream()
                .map(c -> new CityDto(c.name(), c.state(), c.lat(), c.lon())).toList();
    }

    /** Coarse area names per city (never exact addresses), used by the "starting area" pickers. */
    @GetMapping("/localities")
    public Map<String, List<String>> localities() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (City c : gazetteer.all()) {
            if (!c.localities().isEmpty()) {
                out.put(c.name(), c.localities().stream().map(City.Locality::name).toList());
            }
        }
        return out;
    }
}

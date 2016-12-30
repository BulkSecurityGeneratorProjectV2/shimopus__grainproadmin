package pro.grain.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pro.grain.admin.domain.*;
import pro.grain.admin.repository.*;
import pro.grain.admin.repository.search.*;

import javax.inject.Inject;
import java.util.List;

@Service
@Transactional
public class ElasticSearchIndexRegenerateService {
    private final Logger log = LoggerFactory.getLogger(ElasticSearchIndexRegenerateService.class);

    private final StationRepository stationRepository;

    private final StationSearchRepository stationSearchRepository;

    private final PartnerRepository partnerRepository;

    private final PartnerSearchRepository partnerSearchRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private RegionSearchRepository regionSearchRepository;

    @Autowired
    private DistrictRepository districtRepository;

    @Autowired
    private DistrictSearchRepository districtSearchRepository;

    @Autowired
    private LocalityRepository localityRepository;

    @Autowired
    private LocalitySearchRepository localitySearchRepository;

    @Inject
    public ElasticSearchIndexRegenerateService(StationSearchRepository stationSearchRepository, StationRepository stationRepository, PartnerRepository partnerRepository, PartnerSearchRepository partnerSearchRepository) {
        this.stationSearchRepository = stationSearchRepository;
        this.stationRepository = stationRepository;
        this.partnerRepository = partnerRepository;
        this.partnerSearchRepository = partnerSearchRepository;
    }

    public void resetStations() {
        log.debug("Request to reset Station index in Elastic");

        List<Station> stations = this.stationRepository.findAll();
        if (stations != null) {
            this.stationSearchRepository.save(stations);
        }
    }

    public void resetPartners() {
        log.debug("Request to reset Partner index in Elastic");

        List<Partner> partners = this.partnerRepository.findAllWithEagerRelationships();

        if (partners != null && partners.size() != 0) {
            this.partnerSearchRepository.save(partners);
        }
    }

    public void resetRegions() {
        log.debug("Request to reset Regions index in Elastic");

        List<Region> regions = this.regionRepository.findAll();

        if (regions != null) {
            this.regionSearchRepository.save(regions);
        }
    }

    public void resetDistricts() {
        log.debug("Request to reset Districts index in Elastic");

        List<District> districts = this.districtRepository.findAll();

        if (districts != null) {
            this.districtSearchRepository.save(districts);
        }
    }

    public void resetLocalities() {
        log.debug("Request to reset Localities index in Elastic");

        List<Locality> localities = this.localityRepository.findAll();

        if (localities != null) {
            this.localitySearchRepository.save(localities);
        }
    }
}

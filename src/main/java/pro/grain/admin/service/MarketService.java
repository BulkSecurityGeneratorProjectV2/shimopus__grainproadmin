package pro.grain.admin.service;

import com.google.common.base.Stopwatch;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.tofu.SoyTofu;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pro.grain.admin.domain.enumeration.BidType;
import pro.grain.admin.domain.enumeration.NDS;
import pro.grain.admin.service.dto.BidPriceDTO;
import pro.grain.admin.service.dto.StationDTO;
import pro.grain.admin.service.error.MarketGenerationException;
import pro.grain.admin.web.utils.SoyTemplatesUtils;

import javax.inject.Inject;
import javax.xml.crypto.KeySelectorException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Transactional
public class MarketService {
    private final Logger log = LoggerFactory.getLogger(MarketService.class);
    private final BidService bidService;
    private final StationService stationService;

    private final SoyTofu tofu;

    @Inject
    public MarketService(BidService bidService, StationService stationService) throws MarketGenerationException {
        this.bidService = bidService;
        this.stationService = stationService;

        try {
            // Bundle the Soy files for your project into a SoyFileSet.
            SoyFileSet sfs = SoyFileSet.builder()
                .add(getFileFromResources("templates/tables/market-table.soy"))
                .add(getFileFromResources("templates/tables/market-table-download.soy"))
                .add(getFileFromResources("templates/tables/market-table-email-inside.soy"))
                .add(getFileFromResources("templates/tables/market-table-admin.soy"))
                .add(getFileFromResources("templates/tables/market-table-site.soy"))
                .add(getFileFromResources("templates/tables/market-table-site-v2.soy"))
                .build();

            // Compile the template into a SoyTofu object.
            // SoyTofu's newRenderer method returns an object that can render any template in file set.
            tofu = sfs.compileToTofu();
        } catch (IOException e) {
            log.error("Could not open template file for market table", e);
            throw new MarketGenerationException("Could not open template file for market table", e);
        }
    }

//    @Cacheable("MarketReport")
    public String getMarketTableHTML(String stationCode, BidType bidType, String templateName, String baseUrl) {
        return getMarketTableHTML(stationCode, bidType, templateName, baseUrl, -1);
    }

    @Cacheable(value = "MarketReport", condition = "#stationCode==null")
    public String getMarketTableHTML(String stationCode, BidType bidType, String templateName,
                                     String baseUrl, int rowsLimit) {
        log.debug("Generate market HTML table for station code which should be downloaded {}", stationCode);

        if (stationCode != null && stationCode.equals("null")) {
            stationCode = null;
        }

        SoyMapData templateData;
        try {
            templateData = generateCommonParameters(stationCode, bidType, baseUrl, rowsLimit);
        } catch (MarketGenerationException e) {
            return tofu.newRenderer("tables.error")
                .setData(new SoyMapData("errors", SoyTemplatesUtils.objectToSoyData(e.getErrors())))
                .render();
        }
        return tofu.newRenderer("tables." + templateName)
            .setData(templateData)
            .render();
    }

    private SoyMapData generateCommonParameters(String stationCode, BidType bidType, String baseUrl, int rowsLimit) throws MarketGenerationException {
        Collection<ArrayList<BidPriceDTO>> bids = getBids(stationCode, bidType, rowsLimit);

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy");

        return new SoyMapData(
            "currentDate", dateFormat.format(new Date()),
            "station", SoyTemplatesUtils.objectToSoyData(stationService.findOne(stationCode)),
            "baseUrl", baseUrl,
            "adminBaseUrl", "https://grainpro.herokuapp.com/",
            "bids", SoyTemplatesUtils.objectToSoyData(bids),
            "bidType", SoyTemplatesUtils.objectToSoyData(bidType)
        );
    }

    private Collection<ArrayList<BidPriceDTO>> getBids(String stationToCode, BidType bidType, int rowsLimit) throws MarketGenerationException {
        String baseStationToCode;
        List<BidPriceDTO> bids;
        List<String> errors = new ArrayList<>();

        if (stationToCode != null) {
            try {
                baseStationToCode = calculateBaseStation(stationToCode);
                log.warn("Price calc: station code {}", stationToCode);
                Stopwatch timer = Stopwatch.createStarted();
                bids = bidService.getAllCurrentBidsForStation(baseStationToCode, bidType);
                timer.stop();
                log.warn("Price calc: get bids with price {}ms", timer.elapsed(TimeUnit.MILLISECONDS));

                timer = timer.reset();
                timer.start();
                List<BidPriceDTO> fullBids = bidService.getAllCurrentBids(bidType);
                timer.stop();
                log.warn("Price calc: get all bids {}ms", timer.elapsed(TimeUnit.MILLISECONDS));

                //Check for errors
                List<BidPriceDTO> errorForBids = new ArrayList<>(bids.size());


                timer = timer.reset();
                timer.start();
                for (BidPriceDTO fullBidPriceDTO : fullBids) {
                    boolean exists = false;

                    for (BidPriceDTO bidPriceDTO : bids) {
                        if (fullBidPriceDTO.getId().equals(bidPriceDTO.getId())) {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        errorForBids.add(fullBidPriceDTO);
                    }
                }
                timer.stop();
                log.warn("Price calc: sort and check for errors {}ms", timer.elapsed(TimeUnit.MILLISECONDS));

                if (errorForBids.size() != 0) {
                    log.error("Some bids could not be calculated for station " + baseStationToCode);
                    for (BidPriceDTO bid : errorForBids) {
                        String stationFromCode = bid.getElevator().getStationCode();
                        String baseStationFromCode;

                        try {
                            baseStationFromCode = calculateBaseStation(stationFromCode);
                            bid.getElevator().setBaseStationCode(baseStationFromCode);
                        } catch (KeySelectorException e) {
                            errors.add("Невозможно вычислить базовую станцию для станции " + stationFromCode +
                                " (" + bid.getElevator().getStationName() + ")");
                            continue;
                        }

                        //Если станция отправления равна станции прибытия
                        if (baseStationFromCode.equals(baseStationToCode)) {
                            bids.add(bid);
                        } else {
                            errors.add("Нет цены для перевозки из " + baseStationFromCode + " в " + baseStationToCode);
                        }
                    }

                    if (errors.size() > 0) {
                        throw new MarketGenerationException("Some bids could not be calculated for station " + baseStationToCode,
                            errors);
                    }
                }

                timer = timer.reset();
                timer.start();
                Collection<ArrayList<BidPriceDTO>> res = enrichAndSortMarket(bids, stationToCode, baseStationToCode, rowsLimit);
                timer.stop();
                log.warn("Price calc: sort and enrich {}ms", timer.elapsed(TimeUnit.MILLISECONDS));

                return res;

            } catch (KeySelectorException e) {
                log.error("Could not calculate destination station", e);
                errors.add("Невозможно вычислить базовую станцию для станции " + stationToCode);
                throw new MarketGenerationException("Could not calculate destination station",
                    errors
                    , e);
            }
        } else {
            return enrichAndSortMarket(bidService.getAllCurrentBids(bidType), null, null, rowsLimit);
        }
    }

    private String calculateBaseStation(String byStationCode) throws KeySelectorException {
        StationDTO station = stationService.findOne(byStationCode);
        if (station.getRegionId() == null || station.getDistrictId() == null) {
            throw new KeySelectorException(
                String.format("Station with code \"%s\" doesn't have region and/or district. Please specify it.",
                    byStationCode));
        }

        StationDTO newStation = stationService.findByLocation(station.getRegionId(), station.getDistrictId(), station.getLocalityId());

        if (newStation == null) {
            throw new KeySelectorException(
                String.format("Base Station for location \"%s\", \"%s\", \"%s\" was not found. Please specify it.",
                    station.getRegionName(),
                    station.getDistrictName(),
                    station.getLocalityName()));
        }

        return newStation.getCode();
    }

    private Collection<ArrayList<BidPriceDTO>> enrichAndSortMarket(List<BidPriceDTO> bids, String stationToCode,
                                                                   String baseStationToCode, int rowsLimit) {
        if (bids == null) return null;

        Collection<ArrayList<BidPriceDTO>> result = bids.stream()
            .peek(bid -> {
                if (baseStationToCode == null || !baseStationToCode.equals(bid.getElevator().getBaseStationCode()) || bid.getBidType() == BidType.BUY) {
                    Long price = getFCAPrice(bid, stationToCode);
                    if (price != Long.MIN_VALUE) {
                        bid.setFcaPrice(price);
                    }

                    price = getCPTPrice(bid, stationToCode);
                    if (price != Long.MIN_VALUE) {
                        bid.setCptPrice(price);
                    }
                }
            })
            .collect(Collectors.groupingBy(BidPriceDTO::getQualityClass, TreeMap::new,
                Collectors.collectingAndThen(
                    Collectors.toCollection(ArrayList::new),
                    l -> {
                        if (l.get(0).getBidType() == BidType.BUY) {
                            l.sort(Comparator.comparingLong(bid -> getPriceToCompare((BidPriceDTO) bid, stationToCode, baseStationToCode)).reversed());
                        } else {
                            l.sort(Comparator.comparingLong(bid -> getPriceToCompare(bid, stationToCode, baseStationToCode)));
                        }
                        return l;
                    }
                )))
            .values();

        if (rowsLimit >= 0) {
            Collection<ArrayList<BidPriceDTO>> limitedResult = new ArrayList<>();
            int alreadyIntake = 0;

            for (ArrayList<BidPriceDTO> classGroup : result) {
                if (alreadyIntake >= rowsLimit) {
                    break;
                }

                if (classGroup.size() > rowsLimit - alreadyIntake) {
                    limitedResult.add((ArrayList<BidPriceDTO>)classGroup.stream().limit(rowsLimit - alreadyIntake).collect(Collectors.toList()));
                    alreadyIntake = rowsLimit;
                } else {
                    limitedResult.add(classGroup);
                    alreadyIntake += classGroup.size();
                }
            }
            return limitedResult;
        } else {
            return result;
        }
    }

    private Long getPriceToCompare(BidPriceDTO bid, String stationToCode, String baseStationToCode) {
        if (bid.getBidType() == BidType.SELL) {
            if (stationToCode == null) {
                return getFCAPrice(bid, null);
            } else {
                //Если станция отгрузки равна станции доставки
                if (baseStationToCode.equals(bid.getElevator().getBaseStationCode())) {
                    return bid.getPrice();
                } else {
                    return getCPTPrice(bid, stationToCode);
                }
            }
        } else if (bid.getBidType() == BidType.BUY) {
            if (stationToCode == null) {
                return getCPTPrice(bid, stationToCode);
            } else {
                return getFCAPrice(bid, stationToCode);
            }
        }

        return 0L;
    }

    private Long getFCAPrice(BidPriceDTO bid, String stationCode) {
        log.debug("BID {}", bid.getBidType());
        if (bid.getBidType() == BidType.SELL) {
            Long selfPrice = bid.getPrice();
            log.debug("BID price {}", selfPrice);
            Long loadPrice = 0L;

            if (bid.getElevator().getServicePrices() != null && bid.getElevator().getServicePrices().size() > 0) {
                loadPrice = bid.getElevator().getServicePrices().iterator().next().getPrice();
            }

            log.debug("BID load price {}", loadPrice);

            return selfPrice + loadPrice;
        } else if (bid.getBidType() == BidType.BUY) {
            log.debug("BID {}", bid.getBidType());
            if (stationCode == null) return Long.MIN_VALUE;

            return getCPTPrice(bid, stationCode) - getTransportationPrice(bid);
        }

        return Long.MIN_VALUE;
    }

    private Long getCPTPrice(BidPriceDTO bid, String stationCode) {
        if (bid.getBidType() == BidType.SELL) {

            if (stationCode == null) return Long.MIN_VALUE;

            return getFCAPrice(bid, stationCode) + getTransportationPrice(bid);
        } else if (bid.getBidType() == BidType.BUY) {
            return bid.getPrice();
        }

        return Long.MIN_VALUE;
    }

    private Long getTransportationPrice(BidPriceDTO bid) {

        Long transpPrice = 0L;

        if (bid.getNds().equals(NDS.EXCLUDED) && bid.getTransportationPricePrice() != null) {
            transpPrice = bid.getTransportationPricePrice();
        } else if (bid.getNds().equals(NDS.INCLUDED) && bid.getTransportationPricePriceNds() != null) {
            transpPrice = bid.getTransportationPricePriceNds();
        }

        return transpPrice;
    }

    private File getFileFromResources(String path) throws IOException {
        File tempFile = Files.createTempFile("tmp", null).toFile();
        tempFile.deleteOnExit();
        FileOutputStream out = new FileOutputStream(tempFile);
        IOUtils.copy(new ClassPathResource(path).getInputStream(), out);
        return tempFile;
    }

}

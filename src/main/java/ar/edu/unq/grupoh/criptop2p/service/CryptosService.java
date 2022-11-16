package ar.edu.unq.grupoh.criptop2p.service;

import ar.edu.unq.grupoh.criptop2p.exceptions.CryptoException;
import ar.edu.unq.grupoh.criptop2p.model.Cryptocurrency;
import ar.edu.unq.grupoh.criptop2p.model.enums.CriptosNames;
import ar.edu.unq.grupoh.criptop2p.repositories.CryptoRepository;
import ar.edu.unq.grupoh.criptop2p.service.response.BinanceResponse;
import ar.edu.unq.grupoh.criptop2p.service.response.CotizationUSDToARS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@EnableScheduling
public class CryptosService {

    @Autowired
    private CryptoRepository cryptoCurrencyRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public List<Cryptocurrency> findAll() {
        return cryptoCurrencyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Cryptocurrency findCryptoValueByName(CriptosNames cryptoName) {
        return cryptoCurrencyRepository.findAll()
                .stream()
                .filter(crypto -> crypto.getCrypto() == cryptoName)
                .collect(Collectors.collectingAndThen(Collectors.maxBy(Comparator.comparing(Cryptocurrency::getDate)), Optional::get));
    }

    @Transactional
    public List<Cryptocurrency> cryptoLast24hours(CriptosNames cryptoName) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start2 = end.minusHours(24) ;//Para que sea cada 24hr //end.minusMinutes(4);
        List<Cryptocurrency> cryptos = findByCrypto(cryptoName)
                .stream()
                .filter(cryptoCurrency -> cryptoCurrency.getDate().isBefore(ChronoLocalDateTime.from(end)) && cryptoCurrency.getDate().isAfter(ChronoLocalDateTime.from(start2)))
                .collect(Collectors.toList());
        return cryptos;
    }

    @Transactional
    public List<Cryptocurrency> cryptoBetween(String cryptoName, LocalDateTime start, LocalDateTime end) {
        CriptosNames crypto = CriptosNames.valueOf(cryptoName);
        List<Cryptocurrency> cryptos = findByCrypto(crypto)
                .stream()
                .filter(cryptoCurrency -> cryptoCurrency.getDate().isBefore(ChronoLocalDateTime.from(end)) && cryptoCurrency.getDate().isAfter(ChronoLocalDateTime.from(start)))
                .collect(Collectors.toList());
        return cryptos;
    }

    @Transactional
    public Cryptocurrency getCryptoCurrency(CriptosNames cryptoName) {
        Float binancePrice = this.getBinanceResponse(cryptoName).getPrice();
//        Float usdPrice = this.getUSDResponse().getVenta();
//        Float price = binancePrice * usdPrice;
        return new Cryptocurrency(cryptoName, binancePrice);
    }

    @Transactional
    public List<Cryptocurrency> getLastCryptoCurrency() {
        List<CriptosNames> cryptoNames = Arrays.asList(CriptosNames.values());
        return cryptoNames
                .stream()
                .map (crypto -> Collections.max(findByCrypto(crypto), Comparator.comparing(Cryptocurrency::getDate)))
                .collect(Collectors.toList()) ;
    }


    @Transactional
    @Scheduled(cron = "0 0/5 * * * *") // cron = "0 0/10 * * * *" para que sea cada 10m
    public List<Cryptocurrency> updateAllCryptos() {
        List<Cryptocurrency> cryptoCurrencyList = new ArrayList<>();
        BinanceResponse[] binanceCryptoDTOS = getAllCryptoPrice(List.of(CriptosNames.values()));
        Arrays.stream(binanceCryptoDTOS).forEach(binanceCrypto -> {
            try {
                Cryptocurrency crypto = binanceToModel(binanceCrypto);
                cryptoCurrencyList.add(crypto);
                cryptoCurrencyRepository.save(crypto);
            } catch (CryptoException e) {

            }
        });
        return cryptoCurrencyList;
    }

    private BinanceResponse getBinanceResponse(CriptosNames cryptoName) {
            String url = "https://api1.binance.com/api/v3/ticker/price?symbol=" + cryptoName.name();
            BinanceResponse br = restTemplate.getForObject(url, BinanceResponse.class);
            return br != null ? br : new BinanceResponse();
    }

    public CotizationUSDToARS getUSDCotization() {
            String url = "https://api-dolar-argentina.herokuapp.com/api/dolaroficial";
            CotizationUSDToARS br = restTemplate.getForObject(url, CotizationUSDToARS.class);
            return br != null ? br : new CotizationUSDToARS(1F,1F);
    }

    private List<Cryptocurrency> findByCrypto(CriptosNames cryptoName){
        return cryptoCurrencyRepository.findAll().stream().filter(cryptoCurrency -> cryptoCurrency.getCrypto() == cryptoName).collect(Collectors.toList());
    }

    private Cryptocurrency binanceToModel(BinanceResponse binanceCryptoDTO) throws CryptoException {
        Cryptocurrency cryptoCurrency = Cryptocurrency.builder()
                .withCryptoCurrency(binanceCryptoDTO.getSymbol())
                .withPrice(binanceCryptoDTO.getPrice())
                .withDate(LocalDateTime.now())
                .build();
        return cryptoCurrency;
    }

    private BinanceResponse[] getAllCryptoPrice(List<CriptosNames> cryptoNameList) {
        RestTemplate restTemplate = new RestTemplate();
        String cryptoSymbols = cryptoNameList.stream()
                .map(crypto -> '\"' + crypto.name() + '\"')
                .collect(Collectors.joining(",", "[", "]"));

        return restTemplate.getForObject("https://api1.binance.com/api/v3/ticker/price?symbols=" + cryptoSymbols, BinanceResponse[].class);
    }

}

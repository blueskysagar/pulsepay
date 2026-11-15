package com.pulsepay.wallet_service.service.cache;

import com.pulsepay.wallet_service.dto.response.CreateWalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletCacheService {
    private static final Duration TTL = Duration.ofSeconds(60);
    private final StringRedisTemplate redisTemplate;//Interract with redis to store, fetch and delete key-value pairs
    private final JsonMapper jsonMapper;
    public CreateWalletResponse get(UUID accountId, UUID walletId) {
        String key = WalletCacheKey.forWallet(accountId, walletId);


        try {
            String cachedValue = redisTemplate.opsForValue().get(key); //Telling spring hey redis template i want to work with simple key value pairs, give me helper methods for basic string like get,set.
            if (cachedValue == null) return null;
            try {
                return jsonMapper.readValue( // tells Jackson take this json string of cachedValue and convert it into createwalletresponse object.
                        cachedValue,
                        CreateWalletResponse.class
                );
            } catch (JacksonException exception) {
                throw new IllegalStateException("Failed to deserialize cache wallet: " + walletId, exception);
            }
        } catch (RedisConnectionFailureException exception){
            return null;
        }
    }
    public void put(
            UUID accountId,
            UUID walletId,
            CreateWalletResponse wallet
    ){
        String key = WalletCacheKey.forWallet(accountId, walletId);
        String cachedValue;
        //First convert our java object into Json if this fails it is an application/data problem
        try {
            cachedValue = jsonMapper.writeValueAsString(wallet);
        }catch (JacksonException exception){
                throw new IllegalStateException(
                        "Failed to serialize wallet for cache: " + walletId,
                        exception
                );
            }
        //Now talk to the redis //redis failure should not break the successful Db operation
try{
            redisTemplate.opsForValue().set(
                    key,
                    cachedValue,
                    TTL
            );
        }catch (RedisConnectionFailureException exception){
    return;
}
    }
    public void evict(UUID accountId, UUID walletId){
        String key = WalletCacheKey.forWallet(accountId, walletId);
        try{
        redisTemplate.delete(key);
        } catch (RedisConnectionFailureException exception){
            return;
        }
    }
    }
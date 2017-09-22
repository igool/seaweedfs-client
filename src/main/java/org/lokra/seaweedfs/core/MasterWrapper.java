/*
 * Copyright (c) 2016 Lokra Studio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.lokra.seaweedfs.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.methods.HttpGet;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.lokra.seaweedfs.core.contect.*;
import org.lokra.seaweedfs.core.http.JsonResponse;
import org.lokra.seaweedfs.core.topology.DataCenter;
import org.lokra.seaweedfs.core.topology.SystemTopologyStatus;
import org.lokra.seaweedfs.exception.SeaweedfsException;
import org.lokra.seaweedfs.util.RequestPathStrategy;

import java.io.IOException;
import java.util.List;

import static org.lokra.seaweedfs.core.Connection.LOOKUP_VOLUME_CACHE_ALIAS;

/**
 * Master server operation wrapper.
 *
 * @author Chiho Sin
 */
class MasterWrapper {
    private static final Log log = LogFactory.getLog(MasterWrapper.class);
    private Connection connection;
    private Cache<Long, LookupVolumeResult> lookupVolumeCache;
    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructor.
     *
     * @param connection Connection from file source.
     */
    MasterWrapper(Connection connection) {
        this.connection = connection;
        final CacheManager cacheManager = connection.getCacheManager();
        if (cacheManager != null)
            this.lookupVolumeCache =
                    cacheManager.getCache(LOOKUP_VOLUME_CACHE_ALIAS, Long.class, LookupVolumeResult.class);
    }

    /**
     * Assign a file key.
     *
     * @param params Assign file key params.
     * @return Assign file key result.
     * @throws IOException Http connection is fail or server response within some error message.
     */
    AssignFileKeyResult assignFileKey(AssignFileKeyParams params) throws IOException {
        checkConnection();
        //增加可用volume的判断
        SystemTopologyStatus systemTopologyStatus = connection.getSystemTopologyStatus();
        List<DataCenter> dataCenterList = systemTopologyStatus.getDataCenters();
        params.setDataCenter(getOneAvailableDataCenter(dataCenterList).getId());
        log.info(" datacenter "+getOneAvailableDataCenter(dataCenterList) +" url "+params.toUrlParams());
        final String url = connection.getLeaderUrl()+ RequestPathStrategy.assignFileKey + params.toUrlParams();
        HttpGet request = new HttpGet(url);
        JsonResponse jsonResponse = connection.fetchJsonResultByRequest(request);
        return objectMapper.readValue(jsonResponse.json, AssignFileKeyResult.class);
    }

    /***
     * 得到可用的datacenter
     * @param List<DataCenter> dataCenterList 当前的数据volume集合
     * @return DataCenter 得到一个可用的集合
     * **/
    private DataCenter getOneAvailableDataCenter(List<DataCenter> dataCenterList){
        for(DataCenter dataCenter : dataCenterList ){
            if ( dataCenter.getFree() != 0 ){
                return dataCenter;
            }
        }
        //如果走到这里，说明也没有可用的空间，直接返回第一个存储volume
        return dataCenterList.get(0);
    }

    /**
     * Force garbage collection.
     *
     * @param params Force garbage collection params.
     * @throws IOException Http connection is fail or server response within some error message.
     */
    void forceGarbageCollection(ForceGarbageCollectionParams params) throws IOException {
        checkConnection();
        final String url = connection.getLeaderUrl() + RequestPathStrategy.forceGarbageCollection + params.toUrlParams();
        HttpGet request = new HttpGet(url);
        connection.fetchJsonResultByRequest(request);
    }

    /**
     * Pre-Allocate volumes.
     *
     * @param params pre allocate volumes params.
     * @return pre allocate volumes result.
     * @throws IOException Http connection is fail or server response within some error message.
     */
    PreAllocateVolumesResult preAllocateVolumes(PreAllocateVolumesParams params) throws IOException {
        checkConnection();
        final String url = connection.getLeaderUrl() + RequestPathStrategy.preAllocateVolumes + params.toUrlParams();
        HttpGet request = new HttpGet(url);
        JsonResponse jsonResponse = connection.fetchJsonResultByRequest(request);
        return objectMapper.readValue(jsonResponse.json, PreAllocateVolumesResult.class);
    }

    /**
     * Lookup volume.
     *
     * @param params Lookup volume params.
     * @return Lookup volume result.
     * @throws IOException Http connection is fail or server response within some error message.
     */
    LookupVolumeResult lookupVolume(LookupVolumeParams params) throws IOException {
        checkConnection();
        LookupVolumeResult result;
        if (this.lookupVolumeCache != null) {
            result = this.lookupVolumeCache.get(params.getVolumeId());
            if (result != null) {
                return result;
            } else {
                result = fetchLookupVolumeResult(params);
                this.lookupVolumeCache.put(params.getVolumeId(), result);
                return result;
            }
        } else {
            return fetchLookupVolumeResult(params);
        }
    }

    /**
     * Fetch lookup volume result.
     *
     * @param params fetch lookup volume params.
     * @return fetch lookup volume result.
     * @throws IOException Http connection is fail or server response within some error message.
     */
    private LookupVolumeResult fetchLookupVolumeResult(LookupVolumeParams params) throws IOException {
        checkConnection();
        final String url = connection.getLeaderUrl() + RequestPathStrategy.lookupVolume + params.toUrlParams();
        HttpGet request = new HttpGet(url);
        JsonResponse jsonResponse = connection.fetchJsonResultByRequest(request);
        return objectMapper.readValue(jsonResponse.json, LookupVolumeResult.class);
    }

    /**
     * Check connection is alive.
     *
     * @throws SeaweedfsException Http connection is fail.
     */
    private void checkConnection() throws SeaweedfsException {
        if (this.connection.isConnectionClose())
            throw new SeaweedfsException("connection is closed");
    }

}

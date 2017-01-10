/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartloli.kafka.eagle.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartloli.kafka.eagle.domain.ConsumerDetailDomain;
import org.smartloli.kafka.eagle.domain.ConsumerDomain;
import org.smartloli.kafka.eagle.domain.ConsumerPageDomain;
import org.smartloli.kafka.eagle.domain.TupleDomain;
import org.smartloli.kafka.eagle.ipc.RpcClient;
import org.smartloli.kafka.eagle.util.ConstantUtils;
import org.smartloli.kafka.eagle.util.KafkaClusterUtils;
import org.smartloli.kafka.eagle.util.LRUCacheUtils;

/**
 * Get active topic and assembly data.
 *
 * @author smartloli.
 *
 *         Created by Aug 15, 2016
 */
public class ConsumerService {

	private static LRUCacheUtils<String, TupleDomain> lruCache = new LRUCacheUtils<String, TupleDomain>(100000);
	private final static Logger LOG = LoggerFactory.getLogger(ConsumerService.class);

	public static String getActiveTopic() {
		JSONObject obj = new JSONObject();
		obj.put("active", getActive());
		return obj.toJSONString();
	}

	public static String getActiveTopic(String formatter) {
		if ("kafka".equals(formatter)) {
			return getKafkaActiveTopic();
		} else {
			return getActiveTopic();
		}
	}

	private static String getKafkaActiveTopic() {
		JSONObject obj = new JSONObject();
		obj.put("active", getKafkaActive());
		return obj.toJSONString();
	}

	private static Object getKafkaActive() {
		Map<String, List<String>> type = new HashMap<String, List<String>>();
		Gson gson = new Gson();
		Map<String, List<String>> kafka = gson.fromJson(RpcClient.getActiverConsumer(), type.getClass());
		JSONObject obj = new JSONObject();
		JSONArray arrParent = new JSONArray();
		obj.put("name", "Active Topics");
		int count = 0;
		for (Entry<String, List<String>> entry : kafka.entrySet()) {
			JSONObject object = new JSONObject();
			JSONArray arrChild = new JSONArray();
			if (count > ConstantUtils.D3.SIZE) {
				object.put("name", "...");
				JSONObject objectChild = new JSONObject();
				objectChild.put("name", "...");
				arrChild.add(objectChild);
				object.put("children", arrChild);
				arrParent.add(object);
				break;
			} else {
				object.put("name", entry.getKey());
				for (String str : entry.getValue()) {
					JSONObject objectChild = new JSONObject();
					objectChild.put("name", str);
					arrChild.add(objectChild);
				}
			}
			count++;
			object.put("children", arrChild);
			arrParent.add(object);
		}
		obj.put("children", arrParent);
		return obj.toJSONString();
	}

	/**
	 * List the name of the topic in the consumer and whether the topic is
	 * consuming.
	 * 
	 * @return
	 */
	public static String getConsumerDetail(String group, String ip) {
		String key = group + "_consumer_detail_" + ip;
		String ret = "";
		if (lruCache.containsKey(key)) {
			TupleDomain tuple = lruCache.get(key);
			ret = tuple.getRet();
			long end = System.currentTimeMillis();
			if ((end - tuple.getTimespan()) / (1000 * 60.0) > 1) {// 1 mins
				lruCache.remove(key);
			}
		} else {
			Map<String, List<String>> map = KafkaClusterUtils.getConsumers();
			Map<String, List<String>> actvTopics = KafkaClusterUtils.getActiveTopic();
			List<ConsumerDetailDomain> list = new ArrayList<ConsumerDetailDomain>();
			int id = 0;
			for (String topic : map.get(group)) {
				ConsumerDetailDomain consumerDetail = new ConsumerDetailDomain();
				consumerDetail.setId(++id);
				consumerDetail.setTopic(topic);
				if (actvTopics.containsKey(group + "_" + topic)) {
					consumerDetail.setConsumering(true);
				} else {
					consumerDetail.setConsumering(false);
				}
				list.add(consumerDetail);
			}
			ret = list.toString();
			TupleDomain tuple = new TupleDomain();
			tuple.setRet(ret);
			tuple.setTimespan(System.currentTimeMillis());
			lruCache.put(key, tuple);
		}

		return ret;
	}

	public static String getConsumerDetail(String formatter, String group, String ip) {
		if ("kafka".equals(formatter)) {
			return getKafkaConsumerDetail(group, ip);
		} else {
			return getConsumerDetail(group, ip);
		}
	}

	public static String getKafkaConsumerDetail(String group, String ip) {
		String key = group + "_kafka_consumer_detail_" + ip;
		String ret = "";
		if (lruCache.containsKey(key)) {
			TupleDomain tuple = lruCache.get(key);
			ret = tuple.getRet();
			long end = System.currentTimeMillis();
			if ((end - tuple.getTimespan()) / (1000 * 60.0) > 1) {// 1 mins
				lruCache.remove(key);
			}
		} else {
			Map<String, List<String>> type = new HashMap<String, List<String>>();
			Gson gson = new Gson();
			Map<String, List<String>> map = gson.fromJson(RpcClient.getConsumer(), type.getClass());
			Map<String, List<String>> actvTopics = gson.fromJson(RpcClient.getActiverConsumer(), type.getClass());
			List<ConsumerDetailDomain> list = new ArrayList<ConsumerDetailDomain>();
			int id = 0;
			for (String topic : map.get(group)) {
				ConsumerDetailDomain consumerDetail = new ConsumerDetailDomain();
				consumerDetail.setId(++id);
				consumerDetail.setTopic(topic);
				if (actvTopics.containsKey(group + "_" + topic)) {
					consumerDetail.setConsumering(true);
				} else {
					consumerDetail.setConsumering(false);
				}
				list.add(consumerDetail);
			}
			ret = list.toString();
			TupleDomain tuple = new TupleDomain();
			tuple.setRet(ret);
			tuple.setTimespan(System.currentTimeMillis());
			lruCache.put(key, tuple);
		}

		return ret;
	}

	public static String getConsumer(ConsumerPageDomain page) {
		Map<String, List<String>> map = KafkaClusterUtils.getConsumers(page);
		List<ConsumerDomain> list = new ArrayList<ConsumerDomain>();
		int id = 0;
		for (Entry<String, List<String>> entry : map.entrySet()) {
			ConsumerDomain consumer = new ConsumerDomain();
			consumer.setGroup(entry.getKey());
			consumer.setConsumerNumber(entry.getValue().size());
			consumer.setTopic(entry.getValue());
			consumer.setId(++id);
			consumer.setActiveNumber(getActiveNumber(entry.getKey(), entry.getValue()));
			list.add(consumer);
		}
		return list.toString();
	}

	public static String getConsumer(String formatter, ConsumerPageDomain page) {
		if ("kafka".equals(formatter)) {
			return getKafkaConsumer(page);
		} else {
			return getConsumer(page);
		}
	}

	public static int getConsumerCount(String formatter) {
		if ("kafka".equals(formatter)) {
			Map<String, List<String>> map = new HashMap<>();
			try {
				Map<String, List<String>> type = new HashMap<String, List<String>>();
				Gson gson = new Gson();
				map = gson.fromJson(RpcClient.getConsumer(), type.getClass());
			} catch (Exception e) {
				LOG.error("Get Kafka topic offset has error,msg is " + e.getMessage());
			}
			return map.size();
		} else {
			return KafkaClusterUtils.getConsumers().size();
		}
	}

	private static String getKafkaConsumer(ConsumerPageDomain page) {
		List<ConsumerDomain> list = new ArrayList<ConsumerDomain>();
		Map<String, List<String>> type = new HashMap<String, List<String>>();
		Gson gson = new Gson();
		Map<String, List<String>> map = gson.fromJson(RpcClient.getConsumerPage(page), type.getClass());
		int id = 0;
		for (Entry<String, List<String>> entry : map.entrySet()) {
			ConsumerDomain consumer = new ConsumerDomain();
			consumer.setGroup(entry.getKey());
			consumer.setConsumerNumber(entry.getValue().size());
			consumer.setTopic(entry.getValue());
			consumer.setId(++id);
			consumer.setActiveNumber(getKafkaActiveNumber(entry.getKey(), entry.getValue()));
			list.add(consumer);
		}
		return list.toString();
	}

	private static int getKafkaActiveNumber(String group, List<String> topics) {
		Map<String, List<String>> type = new HashMap<String, List<String>>();
		Gson gson = new Gson();
		Map<String, List<String>> map = gson.fromJson(RpcClient.getActiverConsumer(), type.getClass());
		int sum = 0;
		for (String topic : topics) {
			String key = group + "_" + topic;
			if (map.containsKey(key)) {
				sum++;
			}
		}
		return sum;
	}

	public static int getActiveNumber(String group, List<String> topics) {
		Map<String, List<String>> kafka = KafkaClusterUtils.getActiveTopic();
		int sum = 0;
		for (String topic : topics) {
			if (kafka.containsKey(group + "_" + topic)) {
				sum++;
			}
		}
		return sum;
	}

	public static int getConsumerNumbers() {
		Map<String, List<String>> map = KafkaClusterUtils.getConsumers();
		int count = 0;
		for (Entry<String, List<String>> entry : map.entrySet()) {
			count += entry.getValue().size();
		}
		return count;
	}

	public static int getKafkaConsumerNumbers() {
		Map<String, List<String>> type = new HashMap<String, List<String>>();
		Gson gson = new Gson();
		Map<String, List<String>> map = gson.fromJson(RpcClient.getConsumer(), type.getClass());
		int count = 0;
		for (Entry<String, List<String>> entry : map.entrySet()) {
			count += entry.getValue().size();
		}
		return count;
	}

	private static String getActive() {
		Map<String, List<String>> kafka = KafkaClusterUtils.getActiveTopic();
		JSONObject obj = new JSONObject();
		JSONArray arrParent = new JSONArray();
		obj.put("name", "Active Topics");
		int count = 0;
		for (Entry<String, List<String>> entry : kafka.entrySet()) {
			JSONObject object = new JSONObject();
			JSONArray arrChild = new JSONArray();
			if (count > ConstantUtils.D3.SIZE) {
				object.put("name", "...");
				JSONObject objectChild = new JSONObject();
				objectChild.put("name", "...");
				arrChild.add(objectChild);
				object.put("children", arrChild);
				arrParent.add(object);
				break;
			} else {
				object.put("name", entry.getKey());
				for (String str : entry.getValue()) {
					JSONObject objectChild = new JSONObject();
					objectChild.put("name", str);
					arrChild.add(objectChild);
				}
			}
			count++;
			object.put("children", arrChild);
			arrParent.add(object);
		}
		obj.put("children", arrParent);
		return obj.toJSONString();
	}

}

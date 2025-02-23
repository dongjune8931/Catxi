package com.example.catxi.kakaomap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class KakaoMapService {

	@Value("${kakao.map.api.key}")
	private String kakaoApiKey;

	private final RestTemplate restTemplate = new RestTemplate();

	public Coordinates getCoordinates(String address) {
		String url = "https://dapi.kakao.com/v2/local/search/address.json?query=" + address;

		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "KakaoAK " + kakaoApiKey);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
		JSONObject jsonObject = new JSONObject(response.getBody());
		JSONArray documents = jsonObject.getJSONArray("documents");
		if (documents.length() > 0) {
			JSONObject doc = documents.getJSONObject(0);
			double longitude = doc.getDouble("x");
			double latitude = doc.getDouble("y");
			return new Coordinates(latitude, longitude);
		}
		return null;
	}

	public static class Coordinates {
		private final double latitude;
		private final double longitude;

		public Coordinates(double latitude, double longitude) {
			this.latitude = latitude;
			this.longitude = longitude;
		}
		public double getLatitude() { return latitude; }
		public double getLongitude() { return longitude; }
	}
}

package com.example.catxi.oauthjwt;


public interface OAuth2Response {
	String getProvider();
	String getProviderId();
	String getEmail();
	String getName();
}
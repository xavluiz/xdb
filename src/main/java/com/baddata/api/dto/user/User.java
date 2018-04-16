/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.user;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.joda.time.DateTime;

import com.baddata.api.dto.TypedObject;
import com.google.common.base.Strings;

@XmlRootElement
public class User extends TypedObject {

    private String phone;
    private String fullname;
    private DateTime birthdate;
    private String avatar;
    private String profileBackgroundImg;
    private String description;
    
    //
    // This will be the same as the auth.username if
    // the user uses their email as the username
    private String email;
    
    //
    // this can be an email or normal username string
    //
    private String username;
    private String password;
    private String currentPassword;
    private String role;
    private String authToken;

    //
    // This is set if the users logs in with something like
    // google.  Google provides the imageUrl (avatar) which
    // then we'll use to set the "User.avatar" value.
    private String imageUrl;
    private String tokenObj;
    
    private boolean completedTour;
    private boolean requiresSignupValidation;
    
    public enum RoleType {
        
        USER, ADMIN, AUDITOR, SUPPORT, FACEBOOK, GOOGLE, SALESFORCE;
        
        public static RoleType getRoleTypeFromValue( String value ) {
            if ( Strings.isNullOrEmpty(value) ) {
                return RoleType.USER;
            }
            
            // try to get it by it's display name
            for ( RoleType type : RoleType.values() ) {
                if ( type.name().equalsIgnoreCase( value ) ) {
                    return type;
                }
            }
            return RoleType.USER;
        }
    }

    public boolean isCompletedTour() {
        return completedTour;
    }

    public void setCompletedTour(boolean completedTour) {
        this.completedTour = completedTour;
    }

    public boolean isRequiresSignupValidation() {
        return requiresSignupValidation;
    }

    public void setRequiresSignupValidation(boolean requiresSignupValidation) {
        this.requiresSignupValidation = requiresSignupValidation;
    }

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getFullname() {
        return fullname;
    }

    public void setFullname(String fullname) {
        this.fullname = fullname;
    }

    @XmlJavaTypeAdapter(value=com.baddata.api.config.DateTimeXmlAdapter.class)
    public DateTime getBirthdate() {
        return birthdate;
    }

    public void setBirthdate(DateTime birthdate) {
        this.birthdate = birthdate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

	public String getAvatar() {
		return avatar;
	}

	public void setAvatar(String avatar) {
		this.avatar = avatar;
	}

	public String getProfileBackgroundImg() {
		return profileBackgroundImg;
	}

	public void setProfileBackgroundImg(String profileBackgroundImg) {
		this.profileBackgroundImg = profileBackgroundImg;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	
	public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getCurrentPassword() {
        return currentPassword;
    }
    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getRole() {
        return role;
    }
    public void setRole(String role) {
        this.role = role;
    }
    
    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getImageUrl() {
        return imageUrl;
    }
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
    public String getTokenObj() {
        return tokenObj;
    }
    public void setTokenObj(String tokenObj) {
        this.tokenObj = tokenObj;
    }

    @Override
    public User clone() {
        User user = new User();
        user.setAvatar(this.getAvatar());
        user.setBirthdate(this.getBirthdate());
        user.setCompletedTour(this.isCompletedTour());
        user.setCreateTime(this.getCreateTime());
        user.setCurrentPassword(this.getCurrentPassword());
        user.setDescription(this.getDescription());
        user.setEmail(this.getEmail());
        user.setFullname(this.getFullname());
        user.setId(this.getId());
        user.setImageUrl(this.getImageUrl());
        user.setParent(this.getParent());
        user.setPassword(this.getPassword());
        user.setPhone(this.getPhone());
        user.setProfileBackgroundImg(this.getProfileBackgroundImg());
        user.setRequiresSignupValidation(this.isRequiresSignupValidation());
        user.setRole(this.getRole());
        user.setTenantId(this.getTenantId());
        user.setTokenObj(this.getTokenObj());
        user.setUpdateTime(this.getUpdateTime());
        user.setUsername(this.getUsername());
        user.setUserRef(this.getUserRef());
        
        return user;
    }
	
    
}

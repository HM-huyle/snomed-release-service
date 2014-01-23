package org.ihtsdo.buildcloud.entity;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.ihtsdo.buildcloud.helper.EntityHelper;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Product {

	@Id
	@GeneratedValue
	@JsonIgnore
	private Long id;

	private String name;

	@JsonProperty("id")
	private String businessKey;

	@ManyToOne
	@JsonIgnore
	private Extension extension;

	@OneToMany(mappedBy = "product")
	@JsonIgnore
	private Set<Package> packages;

	public Product() {
		packages = new HashSet<>();
	}

	public Product(String name) {
		this();
		setName(name);
	}

	public void addPackage(Package aPackage) {
		packages.add(aPackage);
		aPackage.setProduct(this);
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
		this.businessKey = EntityHelper.formatAsBusinessKey(name);
	}

	public String getBusinessKey() {
		return businessKey;
	}

	public Extension getExtension() {
		return extension;
	}

	public void setExtension(Extension extension) {
		this.extension = extension;
	}

	public Set<Package> getPackages() {
		return packages;
	}
}
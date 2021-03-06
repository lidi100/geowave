/*******************************************************************************
 * Copyright (c) 2013-2017 Contributors to the Eclipse Foundation
 * 
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License,
 * Version 2.0 which accompanies this distribution and is available at
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 ******************************************************************************/
package mil.nga.giat.geowave.adapter.vector.query.cql;

import java.net.MalformedURLException;
import java.nio.ByteBuffer;

import org.geotools.filter.text.ecql.ECQL;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mil.nga.giat.geowave.adapter.vector.GeotoolsFeatureDataAdapter;
import mil.nga.giat.geowave.core.geotime.GeometryUtils;
import mil.nga.giat.geowave.core.index.ByteArrayId;
import mil.nga.giat.geowave.core.index.StringUtils;
import mil.nga.giat.geowave.core.index.persist.PersistenceUtils;
import mil.nga.giat.geowave.core.store.adapter.AbstractAdapterPersistenceEncoding;
import mil.nga.giat.geowave.core.store.adapter.IndexedAdapterPersistenceEncoding;
import mil.nga.giat.geowave.core.store.data.IndexedPersistenceEncoding;
import mil.nga.giat.geowave.core.store.data.PersistentDataset;
import mil.nga.giat.geowave.core.store.filter.DistributableQueryFilter;
import mil.nga.giat.geowave.core.store.index.CommonIndexModel;
import mil.nga.giat.geowave.core.store.index.PrimaryIndex;

public class CQLQueryFilter implements
		DistributableQueryFilter
{
	private final static Logger LOGGER = LoggerFactory.getLogger(CQLQueryFilter.class);
	private GeotoolsFeatureDataAdapter adapter;
	private Filter filter;

	public CQLQueryFilter() {
		super();
	}

	public CQLQueryFilter(
			final Filter filter,
			final GeotoolsFeatureDataAdapter adapter ) {
		this.filter = FilterToCQLTool.fixDWithin(filter);
		this.adapter = adapter;
	}

	public ByteArrayId getAdapterId() {
		return adapter.getAdapterId();
	}

	@Override
	public boolean accept(
			final CommonIndexModel indexModel,
			final IndexedPersistenceEncoding persistenceEncoding ) {
		if ((filter != null) && (indexModel != null) && (adapter != null)) {
			final PersistentDataset<Object> adapterExtendedValues = new PersistentDataset<Object>();
			if (persistenceEncoding instanceof AbstractAdapterPersistenceEncoding) {
				((AbstractAdapterPersistenceEncoding) persistenceEncoding).convertUnknownValues(
						adapter,
						indexModel);
				final PersistentDataset<Object> existingExtValues = ((AbstractAdapterPersistenceEncoding) persistenceEncoding)
						.getAdapterExtendedData();
				if (existingExtValues != null) {
					adapterExtendedValues.addValues(existingExtValues.getValues());
				}
			}
			final IndexedAdapterPersistenceEncoding encoding = new IndexedAdapterPersistenceEncoding(
					persistenceEncoding.getInternalAdapterId(),
					persistenceEncoding.getDataId(),
					persistenceEncoding.getInsertionPartitionKey(),
					persistenceEncoding.getInsertionSortKey(),
					persistenceEncoding.getDuplicateCount(),
					persistenceEncoding.getCommonData(),
					new PersistentDataset<byte[]>(),
					adapterExtendedValues);

			final SimpleFeature feature = adapter.decode(
					encoding,
					new PrimaryIndex(
							null, // because we know the feature data
									// adapter doesn't use the numeric index
									// strategy and only the common index
									// model to decode the simple feature,
									// we pass along a null strategy to
									// eliminate the necessity to send a
									// serialization of the strategy in the
									// options of this iterator
							indexModel));
			if (feature == null) {
				return false;
			}
			return filter.evaluate(feature);

		}
		return true;
	}

	@Override
	public byte[] toBinary() {
		byte[] filterBytes;
		if (filter == null) {
			LOGGER.warn("CQL filter is null");
			filterBytes = new byte[] {};
		}
		else {
			filterBytes = StringUtils.stringToBinary(ECQL.toCQL(filter));
		}
		byte[] adapterBytes;
		if (adapter != null) {
			adapterBytes = PersistenceUtils.toBinary(adapter);
		}
		else {
			LOGGER.warn("Feature Data Adapter is null");
			adapterBytes = new byte[] {};
		}
		final ByteBuffer buf = ByteBuffer.allocate(filterBytes.length + adapterBytes.length + 4);
		buf.putInt(filterBytes.length);
		buf.put(filterBytes);
		buf.put(adapterBytes);
		return buf.array();
	}

	@Override
	public void fromBinary(
			final byte[] bytes ) {
		try {
			GeometryUtils.initClassLoader();
		}
		catch (final MalformedURLException e) {
			LOGGER.error(
					"Unable to initialize GeoTools class loader",
					e);
		}
		final ByteBuffer buf = ByteBuffer.wrap(bytes);
		final int filterBytesLength = buf.getInt();
		final int adapterBytesLength = bytes.length - filterBytesLength - 4;
		if (filterBytesLength > 0) {
			final byte[] filterBytes = new byte[filterBytesLength];
			buf.get(filterBytes);
			final String cql = StringUtils.stringFromBinary(filterBytes);
			try {
				filter = ECQL.toFilter(cql);
			}
			catch (final Exception e) {
				throw new IllegalArgumentException(
						cql,
						e);
			}
		}
		else {
			LOGGER.warn("CQL filter is empty bytes");
			filter = null;
		}

		if (adapterBytesLength > 0) {
			final byte[] adapterBytes = new byte[adapterBytesLength];
			buf.get(adapterBytes);

			try {
				adapter = (GeotoolsFeatureDataAdapter) PersistenceUtils.fromBinary(adapterBytes);
			}
			catch (final Exception e) {
				throw new IllegalArgumentException(
						e);
			}
		}
		else {
			LOGGER.warn("Feature Data Adapter is empty bytes");
			adapter = null;
		}
	}
}

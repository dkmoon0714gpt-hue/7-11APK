package com.chaoshangka.manager.data.local

import com.chaoshangka.manager.domain.model.Barcode
import com.chaoshangka.manager.domain.model.BarcodeFormatType
import com.chaoshangka.manager.domain.model.CardStatus
import com.chaoshangka.manager.domain.model.CardType
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.GiftCard
import com.chaoshangka.manager.domain.model.ProductCategory
import com.chaoshangka.manager.domain.model.RecognitionStatus
import com.chaoshangka.manager.domain.model.StoreType
import java.time.LocalDate

fun GiftCardEntity.toDomain(barcodes: List<Barcode> = emptyList()): GiftCard = GiftCard(
    id = id,
    storeType = StoreType.fromKey(storeType),
    productName = productName,
    categoryId = categoryId,
    denomination = denomination,
    productSpecification = productSpecification,
    quantity = quantity,
    cardType = CardType.fromKey(cardType),
    startDate = startDate?.let { LocalDate.ofEpochDay(it) },
    expiryDate = expiryDate?.let { LocalDate.ofEpochDay(it) },
    expiryStatus = ExpiryStatus.fromKey(expiryStatus),
    cardStatus = CardStatus.fromKey(cardStatus),
    createdAt = createdAt,
    updatedAt = updatedAt,
    usedAt = usedAt,
    originalImagePath = originalImagePath,
    notes = notes,
    isArchived = isArchived,
    cardNumber = cardNumber,
    barcodes = barcodes,
)

fun GiftCard.toEntity(): GiftCardEntity = GiftCardEntity(
    id = id,
    storeType = storeType.key,
    productName = productName,
    categoryId = categoryId,
    denomination = denomination,
    productSpecification = productSpecification,
    quantity = quantity,
    cardType = cardType.key,
    startDate = startDate?.toEpochDay(),
    expiryDate = expiryDate?.toEpochDay(),
    expiryStatus = expiryStatus.key,
    cardStatus = cardStatus.key,
    createdAt = createdAt,
    updatedAt = updatedAt,
    usedAt = usedAt,
    originalImagePath = originalImagePath,
    notes = notes,
    isArchived = isArchived,
    cardNumber = cardNumber,
)

fun BarcodeEntity.toDomain(): Barcode = Barcode(
    id = id,
    giftCardId = giftCardId,
    barcodeFormat = BarcodeFormatType.fromKey(barcodeFormat),
    rawValue = rawValue,
    barcodeImagePath = barcodeImagePath,
    isPrimary = isPrimary,
    recognitionStatus = RecognitionStatus.fromKey(recognitionStatus),
    createdAt = createdAt,
)

fun Barcode.toEntity(): BarcodeEntity = BarcodeEntity(
    id = id,
    giftCardId = giftCardId,
    barcodeFormat = barcodeFormat.key,
    rawValue = rawValue,
    barcodeImagePath = barcodeImagePath,
    isPrimary = isPrimary,
    recognitionStatus = recognitionStatus.key,
    createdAt = createdAt,
)

fun ProductCategoryEntity.toDomain(): ProductCategory = ProductCategory(
    id = id,
    storeType = storeType?.let { StoreType.fromKey(it) },
    categoryName = categoryName,
    denomination = denomination,
    productSpecification = productSpecification,
    isDefault = isDefault,
    isPresetProduct = isPresetProduct,
    isActive = isActive,
    sortOrder = sortOrder,
)

fun ProductCategory.toEntity(): ProductCategoryEntity = ProductCategoryEntity(
    id = id,
    storeType = storeType?.key,
    categoryName = categoryName,
    denomination = denomination,
    productSpecification = productSpecification,
    isDefault = isDefault,
    isPresetProduct = isPresetProduct,
    isActive = isActive,
    sortOrder = sortOrder,
)

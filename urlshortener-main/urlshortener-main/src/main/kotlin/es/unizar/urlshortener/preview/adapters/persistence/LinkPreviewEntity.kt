package es.unizar.urlshortener.preview.adapters.persistence

import es.unizar.urlshortener.preview.application.LinkPreview
import es.unizar.urlshortener.preview.application.LinkPreviewStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "link_preview")
class LinkPreviewEntity(
    @Id
    var hash: String,
    @Column(nullable = false, length = 2048)
    var target: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: LinkPreviewStatus,
    @Column(length = 512)
    var title: String? = null,
    @Column(length = 2048)
    var image: String? = null,
    @Column(length = 2048)
    var url: String? = null,
    @Column(length = 1024)
    var detail: String? = null,
)

fun LinkPreviewEntity.toDomain() =
    LinkPreview(
        hash = hash,
        target = target,
        status = status,
        title = title,
        image = image,
        url = url,
        detail = detail,
    )

fun LinkPreview.toEntity() =
    LinkPreviewEntity(
        hash = hash,
        target = target,
        status = status,
        title = title,
        image = image,
        url = url,
        detail = detail,
    )

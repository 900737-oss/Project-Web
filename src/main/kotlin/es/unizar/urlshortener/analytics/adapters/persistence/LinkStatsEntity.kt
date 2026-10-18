package es.unizar.urlshortener.analytics.adapters.persistence

import es.unizar.urlshortener.analytics.application.LinkStats
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "link_stats")
class LinkStatsEntity(
    @Id
    var hash: String,
    var totalClicks: Long = 0,
    @Column(nullable = false, columnDefinition = "boolean default true")
    var active: Boolean = true,
)

fun LinkStatsEntity.toDomain() = LinkStats(hash = hash, totalClicks = totalClicks, active = active)

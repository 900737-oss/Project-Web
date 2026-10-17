package es.unizar.urlshortener.preview.adapters.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface JpaLinkPreviewRepository : JpaRepository<LinkPreviewEntity, String>

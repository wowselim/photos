import co.selim.thumbnail4j.createThumbnail
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.*

private val homeDirectoryPath = Paths.get(System.getProperty("user.home"))
private val coversPath = homeDirectoryPath.resolve("covers")
private val thumbnailsPath = homeDirectoryPath.resolve("thumbnails")
private val photosPath = homeDirectoryPath.resolve("photos")
private val permittedFileExtensions = setOf("jpg", "jpeg")

fun main() {
    val albums: MutableMap<String, Album> = coversPath.readAlbums()
        .onEach { (albumId, album) ->
            if (Files.notExists(album.path)) {
                error("Album path '${album.path}' not found for album with id $albumId")
            }
            if (Files.notExists(album.coverPhoto)) {
                error("Album cover '${album.coverPhoto}' not found for album with id $albumId")
            }
            album.photos.forEach { (photoId, photo) ->
                if (Files.notExists(photo.path)) {
                    error("Photo with id '$photoId' not found in ${photo.path}")
                }
                if (Files.notExists(photo.thumbnailPath)) {
                    error("Thumbnail for photo with id '$photoId' not found in ${photo.thumbnailPath}")
                }
            }
        }

    val app = Javalin.create { config ->
        config.showJavalinBanner = false
    }.start(8080)

    app.get("/") { ctx -> ctx.redirect("/albums") }

    app.get("/albums") { ctx ->
        val html = buildString {
            append("<html>")
            append("<ul>")
            for ((albumId, album) in albums) {
                append("<li>")
                append("""<a href="/albums/$albumId">""")
                append("""<img width="128px" src="/albums/$albumId/thumbnail">""")
                append(album.name)
                append("</a>")
                append("</li>")
            }
            append("</ul>")
            append("</html>")
        }
        ctx.html(html)
    }

    app.get("/albums/{albumId}") { ctx ->
        val albumId = ctx.pathParam("albumId")
        val album = albums[albumId] ?: throw NotFoundResponse()

        val html = buildString {
            append("<html>")
            append("<ul>")
            for ((photoId, _) in album.photos) {
                append("""<li><img width="128px" src="/albums/$albumId/photos/${photoId}"></li>""")
            }
            append("</ul>")
            append("</html>")
        }
        ctx.html(html)
    }

    app.get("/albums/{albumId}/photos/{photoId}") { ctx ->
        val albumId = ctx.pathParam("albumId")
        val album = albums[albumId] ?: throw NotFoundResponse()
        val photoId = ctx.pathParam("photoId")
        val photo = album.photos[photoId] ?: throw NotFoundResponse()

        ctx.sendJpegImage(photo.path)
    }

    app.get("/albums/{albumId}/thumbnail") { ctx ->
        val albumId = ctx.pathParam("albumId")
        val album = albums[albumId] ?: throw NotFoundResponse()

        ctx.sendJpegImage(album.coverPhoto)
    }

    app.post("/albums/{albumId}/upload") { ctx ->
        val albumId = ctx.pathParam("albumId")
        val album = albums[albumId] ?: throw NotFoundResponse()

        ctx.uploadedFiles().forEach { file ->
            val fileName = randomString(24) + ".jpg"

            val thumbnailPath = album.coverPhoto.resolveSibling(fileName)
            val thumbnailImage = file.content.createThumbnailAndReset(1024)
            Files.write(thumbnailPath, thumbnailImage)

            val photoPath = album.path.resolve(fileName)
            val highResImage = file.content.createThumbnailAndReset(2048)
            Files.write(photoPath, highResImage)

            val photoEntry = photoPath.nameWithoutExtension to Photo(photoPath, thumbnailPath)
            albums[albumId] = album.copy(photos = album.photos + photoEntry)
        }
        ctx.result("OK")
    }
}

private fun Path.readAlbums(): SortedMap<String, Album> = bufferedReader()
    .lineSequence()
    .filter { line -> line.contains("=") }
    .associate { line ->
        val (indexedAlbumName, coverFileName) = line.split("=")
        val name = indexedAlbumName
            .dropWhile { it != '_' }
            .drop(1)

        val albumPath = photosPath.resolve(indexedAlbumName)
        val coverPath = thumbnailsPath.resolve(indexedAlbumName).resolve(coverFileName)

        val photos = albumPath.listDirectoryEntries()
            .filter { photosPath ->
                photosPath.extension.lowercase() in permittedFileExtensions
            }
            .associate { photoPath ->
                photoPath.nameWithoutExtension to Photo(
                    photoPath,
                    thumbnailsPath.resolve(indexedAlbumName).resolve(photoPath.fileName.pathString)
                )
            }

        indexedAlbumName to Album(name, coverPath, albumPath, photos)
    }
    .toSortedMap()

data class Album(
    val name: String,
    val coverPhoto: Path,
    val path: Path,
    val photos: Map<String, Photo>,
)

data class Photo(
    val path: Path,
    val thumbnailPath: Path,
)

private fun Context.sendJpegImage(path: Path) {
    contentType("image/jpeg")
        .header("Cache-Control", "public, max-age=604800, immutable")
        .result(Files.newInputStream(path))
}

private fun InputStream.createThumbnailAndReset(px: Int) = createThumbnail(px).also { reset() }

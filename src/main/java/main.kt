import io.javalin.Javalin
import io.javalin.http.NotFoundResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.*

private val coversPath = Paths.get("/Users/selim/covers")
private val thumbnailsPath = Paths.get("/Users/selim/thumbnails")
private val photosPath = Paths.get("/Users/selim/photos")
private val permittedFileExtensions = setOf("jpg", "jpeg")

fun main() {
    val app = Javalin.create { config ->
        config.showJavalinBanner = false
    }.start(8080)

    val albums = coversPath.readAlbumCovers()
        .onEach { (_, albumCover) ->
            if (Files.notExists(albumCover.path) || Files.notExists(albumCover.coverPhoto)) {
                error("file not found!!!")
            }
        }

    app.get("/") { ctx ->
        val html = buildString {
            append("<html>")
            append("<ul>")
            for ((albumId, album) in albums) {
                append("<li>")
                append("""<a href="/albums/$albumId">""")
                append("""<img width="128px" src="/albums/$albumId/thumbnail">""")
                append("${album.name}")
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
            for ((photoId, photo) in album.photos) {
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

        ctx.contentType("image/jpeg")
            .result(Files.newInputStream(photo.path))
    }

    app.get("/albums/{albumId}/thumbnail") { ctx ->
        val albumId = ctx.pathParam("albumId")
        val album = albums[albumId] ?: throw NotFoundResponse()

        ctx.contentType("image/jpeg")
            .result(Files.newInputStream(album.coverPhoto))
    }
}

private fun Path.readAlbumCovers(): SortedMap<String, Album> = bufferedReader()
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
                    thumbnailsPath.resolve(photoPath.fileName.pathString)
                )
            }

        albumPath.fileName.pathString to Album(name, coverPath, albumPath, photos)
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

private val Photo.thumbnailId: String
    get() = thumbnailsPath.nameWithoutExtension

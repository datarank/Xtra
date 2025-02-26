package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.github.andreyasadchy.xtra.UserVideosQuery
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.type.BroadcastType
import com.github.andreyasadchy.xtra.type.VideoSort
import com.github.andreyasadchy.xtra.util.C

class ChannelVideosDataSource(
    private val channelId: String?,
    private val channelLogin: String?,
    private val helixHeaders: Map<String, String>,
    private val helixPeriod: String,
    private val helixBroadcastTypes: String,
    private val helixSort: String,
    private val helixApi: HelixApi,
    private val gqlHeaders: Map<String, String>,
    private val gqlQueryType: BroadcastType?,
    private val gqlQuerySort: VideoSort?,
    private val gqlType: String?,
    private val gqlSort: String?,
    private val gqlApi: GraphQLRepository,
    private val apolloClient: ApolloClient,
    private val checkIntegrity: Boolean,
    private val apiPref: List<String>,
) : PagingSource<Int, Video>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Video> {
        return try {
            val response = try {
                when (apiPref.getOrNull(0)) {
                    C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL -> if (helixPeriod == "all") { api = C.GQL; gqlQueryLoad(params) } else throw Exception()
                    C.GQL_PERSISTED_QUERY -> if (helixPeriod == "all") { api = C.GQL_PERSISTED_QUERY; gqlLoad(params) } else throw Exception()
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") return LoadResult.Error(e)
                try {
                    when (apiPref.getOrNull(1)) {
                        C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL -> if (helixPeriod == "all") { api = C.GQL; gqlQueryLoad(params) } else throw Exception()
                        C.GQL_PERSISTED_QUERY -> if (helixPeriod == "all") { api = C.GQL_PERSISTED_QUERY; gqlLoad(params) } else throw Exception()
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") return LoadResult.Error(e)
                    try {
                        when (apiPref.getOrNull(2)) {
                            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                            C.GQL -> if (helixPeriod == "all") { api = C.GQL; gqlQueryLoad(params) } else throw Exception()
                            C.GQL_PERSISTED_QUERY -> if (helixPeriod == "all") { api = C.GQL_PERSISTED_QUERY; gqlLoad(params) } else throw Exception()
                            else -> throw Exception()
                        }
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") return LoadResult.Error(e)
                        listOf()
                    }
                }
            }
            LoadResult.Page(
                data = response,
                prevKey = null,
                nextKey = if (!offset.isNullOrBlank() && (api == C.HELIX || nextPage)) {
                    nextPage = false
                    (params.key ?: 1) + 1
                } else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun helixLoad(params: LoadParams<Int>): List<Video> {
        val response = helixApi.getVideos(
            headers = helixHeaders,
            channelId = channelId,
            period = helixPeriod,
            broadcastType = helixBroadcastTypes,
            sort = helixSort,
            limit = params.loadSize,
            offset = offset
        )
        val list = response.data.map {
            Video(
                id = it.id,
                channelId = channelId,
                channelLogin = it.channelLogin,
                channelName = it.channelName,
                title = it.title,
                viewCount = it.viewCount,
                uploadDate = it.uploadDate,
                duration = it.duration,
                thumbnailUrl = it.thumbnailUrl,
            )
        }
        offset = response.pagination?.cursor
        return list
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): List<Video> {
        val response = apolloClient.newBuilder().apply {
            gqlHeaders.entries.forEach { addHttpHeader(it.key, it.value) }
        }.build().query(UserVideosQuery(
            id = if (!channelId.isNullOrBlank()) Optional.Present(channelId) else Optional.Absent,
            login = if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) Optional.Present(channelLogin) else Optional.Absent,
            sort = Optional.Present(gqlQuerySort),
            types = Optional.Present(gqlQueryType?.let { listOf(it) }),
            first = Optional.Present(params.loadSize),
            after = Optional.Present(offset)
        )).execute()
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.user!!
        val items = data.videos!!.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                Video(
                    id = it.id,
                    channelId = channelId,
                    channelLogin = data.login,
                    channelName = data.displayName,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.displayName,
                    type = it.broadcastType?.toString(),
                    title = it.title,
                    viewCount = it.viewCount,
                    uploadDate = it.createdAt?.toString(),
                    duration = it.lengthSeconds?.toString(),
                    thumbnailUrl = it.previewThumbnailURL,
                    profileImageUrl = data.profileImageURL,
                    tags = it.contentTags?.map { tag ->
                        Tag(
                            id = tag.id,
                            name = tag.localizedName
                        )
                    },
                    animatedPreviewURL = it.animatedPreviewURL
                )
            }
        }
        offset = items.lastOrNull()?.cursor?.toString()
        nextPage = data.videos.pageInfo?.hasNextPage != false
        return list
    }

    private suspend fun gqlLoad(params: LoadParams<Int>): List<Video> {
        val response = gqlApi.loadChannelVideos(gqlHeaders, channelLogin, gqlType, gqlSort, params.loadSize, offset)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.user
        val items = data.videos!!.edges
        val list = items.map { item ->
            item.node.let {
                Video(
                    id = it.id,
                    channelId = it.owner?.id,
                    channelLogin = it.owner?.login,
                    channelName = it.owner?.displayName,
                    gameId = it.game?.id,
                    gameName = it.game?.displayName,
                    title = it.title,
                    viewCount = it.viewCount,
                    uploadDate = it.publishedAt,
                    duration = it.lengthSeconds?.toString(),
                    thumbnailUrl = it.previewThumbnailURL,
                    profileImageUrl = it.owner?.profileImageURL,
                    tags = it.contentTags?.map { tag ->
                        Tag(
                            id = tag.id,
                            name = tag.localizedName
                        )
                    },
                    animatedPreviewURL = it.animatedPreviewURL
                )
            }
        }
        offset = items.lastOrNull()?.cursor
        nextPage = data.videos.pageInfo?.hasNextPage != false
        return list
    }

    override fun getRefreshKey(state: PagingState<Int, Video>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}

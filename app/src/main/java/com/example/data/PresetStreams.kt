package com.example.data

data class PresetStream(
    val title: String,
    val description: String,
    val url: String,
    val tag: String
)

object PresetStreams {
    val items = listOf(
        PresetStream(
            title = "Lofi Hip Hop Radio",
            description = "Chill, study, and relax beats 24/7",
            url = "https://stream.zeno.fm/f3wvbbqmdg8uv",
            tag = "Lofi"
        ),
        PresetStream(
            title = "Chillout Lounge & Ambient",
            description = "Deep melodic downtempo vibes",
            url = "https://stream.zeno.fm/0r0xa792kwzuv",
            tag = "Lounge"
        ),
        PresetStream(
            title = "Classic Rock Radio",
            description = "Timeless rock and energetic guitars",
            url = "https://stream.zeno.fm/n9w0yv7382zuv",
            tag = "Rock"
        ),
        PresetStream(
            title = "Classical Masterpieces",
            description = "Orchestral symphonies and piano",
            url = "https://stream.zeno.fm/4v3vbf3mdg8uv",
            tag = "Classical"
        ),
        PresetStream(
            title = "Synthwave 80s Cyber Radio",
            description = "Retro synth, dark electro, and outrun",
            url = "https://stream.zeno.fm/eq2k0b1xmg8uv",
            tag = "Synthwave"
        ),
        PresetStream(
            title = "Sleep & Rain Soundscape",
            description = "Gentle rain and continuous white noise",
            url = "https://stream.zeno.fm/w062e2w8e8uv",
            tag = "Ambient"
        )
    )
}

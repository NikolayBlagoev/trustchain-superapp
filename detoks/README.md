# DeToks

Detoks is a decentralized version of TikTok implemented using [IPv8](https://github.com/Tribler/kotlin-ipv8) and [Trustchain](https://github.com/Tribler/kotlin-ipv8/blob/master/doc/TrustChainCommunity.md).

![image](https://user-images.githubusercontent.com/38362353/233190970-2f559f87-f3b4-44d3-a913-a2892f433bf7.png)
![image](https://user-images.githubusercontent.com/38362353/233191027-0648698a-f1c2-4820-ada7-9de8bb77dfe7.png)
![image](https://user-images.githubusercontent.com/38362353/233191066-e90a032c-1e9e-4ecc-b341-826196046b5f.png)
![image](https://user-images.githubusercontent.com/38362353/233191093-05012641-3cf7-4447-b254-4646d780d7f9.png)
![image](https://user-images.githubusercontent.com/38362353/233191136-e9d8730f-f38c-48e0-b2e3-eb732a487808.png)
![image](https://user-images.githubusercontent.com/38362353/233190331-45f80e65-018b-4d9d-b2c3-7cb8e1a1324a.png)

## Like Token

Each like message is encoded as a trustchain block and shared with the network.
When users receive a like, they retrieve the torrent link from the like message to achieve content discovery.
Furthermore, the user can count the number of likes for each video and use that to recommend new videos based on what is currently trending.
The user can also see how many likes they have received themselves.

The like token has the following format:

| Field     |      Description   |
|-----------|:----------|
| liker     | The public key of the person who liked the video |
| video     | The name of the video liked (since a torrent can have multiple files) |
| torrent   | The name of the torrent (its hash info) |
| author    | The public key of the creator of the video  |
| torrentMagnet | A magnet link for the torrent video (since we can have different magnet links for the same torrent) |
| timestamp   | a simple timestamp indicating the time of the like |

## Recommendation system

The recommender decides which videos the user sees based on the current trends.
The recommender prioritizes videos that the user has not watched yet, it then sorts all the videos that are available to the user by descending number of likes and on ties it selects the more recent one next.

## Torrenting

The torrenting is handled by the TorrentManager class (located in TorrentManager.kt). A single instance needs to exist for it for the entire duration of the app, as different fragments and classes interact with it. Hence why to get access to it, one needs to use TorrentManager.getInstance() method. Example of getting an instance of the current TorrentManager:

```kotlin
torrentManager = TorrentManager.getInstance(requireActivity().applicationContext)
```

Since the user uploads their own videos, clearing the cache is not recommended (as then the app can no longer seed). Thus, currently, all downloaded videos are kept in the cache.

To create a new torrent, one simply needs to call:

```kotlin
// uri is the android media uri of the file to be added to the torrent. This can be received from a call to ActivityResultContracts.GetContent()
torrentManager.createTorrentInfo(uri, context)
```

When a new torrent is created it will automatically be "downloaded", which will result in the device seeding the new video. A new like is broadcasted, since new video announcements are equivalent to sending out the first like. Thus the user will automatically like their own video. The torrent name is set to the name of the video included in it. The author/creator of it is the public key of the node.

Currently, we rely on trackers for the distribution of torrent information (since nodes can be arbitrally on or off). Hence, a custom tracker is also provided. We recommend for redundancy to also use some other public tracker, as this can result in better download speed. As a second tracker we currently use http://opensharing.org:2710/announce

To download a new torrent with a specified magnet link, simply call:
```kotlin

torrentManager.addMagnet(magnet)
```

It will automatically download the video (and start seeding). Torrent files are created in the torrent folder in cache and the contents of the torrents are saved to the media folder in the cache.

## Tracker

As part of the project we provide an http tracker, which will automatically seed all the torrents it receives. It is located in the directory tracker

To start the tracker, first launch the python backend with:
```
python tor.py
```

It will run on localhost and listen on port 8082. It receives a torrent hash and upon receipt will start downloading it (thus also seeding it).

Then start the actual tracker server with:
```

node index.js
```

This will start a new tracker server, listening on port 8080. To announce a new torrent, simply to a call to /announce endpoint.

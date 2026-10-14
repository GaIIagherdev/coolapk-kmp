import Foundation

@main
enum SearchParserTests {
    static func main() {
        precondition(SearchCategory.allCases.map(\.rawValue) == [
            "all", "apk", "game", "user", "feed", "ask", "product", "feedTopic"
        ])

        let page = SearchParser.page(
            #"""
            {"data":[{"entityId":"outer","entityTemplate":"searchCard","entities":[
                {"entityType":"user","uid":"10086","username":"酷友"},
                {"entityTemplate":"sponsorForSearch","sponsorType":"apk","title":"广告"},
                {"entityType":"apk","id":"app-1","apkName":"酷安工具","packageName":"com.example.tool","logo":"//image.coolapk.com/tool.png"},
                {"entityType":"product","id":91,"title":"旗舰手机","logo":"phone.png"},
                {"entityType":"feed","id":92,"message":"动态正文","username":"作者"},
                {"entityType":"feedQuestion","id":93,"message_title":"如何选手机"},
                {"entityType":"feedTopic","id":94,"title":"手机话题"}
            ]}],"pagination":{"firstItem":"a","lastItem":"b","hasMore":true}}
            """#,
            category: .all
        )
        precondition(page.items.map(\.kind) == [.user, .app, .product, .feed, .question, .topic])
        precondition(page.items.map(\.canOpen) == [false, true, true, true, false, false])
        precondition(page.items[1].targetID == "com.example.tool")
        precondition(page.items[1].imageURL?.absoluteString == "https://image.coolapk.com/tool.png")
        precondition(page.items[2].targetID == "91")
        precondition(page.firstItem == "a" && page.lastItem == "b" && page.hasMore)

        let ask = SearchParser.page(
            #"{"data":[{"entityType":"feed","id":11,"message":"普通问题"},{"entityType":"feed","feedType":"answer","id":12,"message":"回答"}]}"#,
            category: .ask
        )
        precondition(ask.items.map(\.kind) == [.question, .feed])
        precondition(ask.items.map(\.canOpen) == [false, true])

        let unnamedPackage = SearchParser.page(
            #"{"data":[{"entityType":"apk","id":15,"apkName":"仅有应用名"}]}"#,
            category: .apk
        )
        precondition(unnamedPackage.items.first?.title == "仅有应用名")
        precondition(unnamedPackage.items.first?.canOpen == false)

        let game = SearchParser.page(
            #"{"data":[{"entityType":"apk","id":16,"apkName":"游戏名","packageName":"com.example.game"}]}"#,
            category: .game
        )
        precondition(game.items.first?.kind == .game)
        precondition(game.items.first?.canOpen == true)

        let hotWords = SearchParser.hotWords(
            #"""
            {"data":[{"entityTemplate":"searchHotCard","entities":[
                {"title":"折叠屏"},{"entityType":"sponsorForSearch","title":"广告"},
                {"keyword":"新手机"},{"title":"折叠屏"}
            ]}]}
            """#
        )
        precondition(hotWords == ["折叠屏", "新手机"])
        print("SearchParserTests passed")
    }
}

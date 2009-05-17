function makeHtml(md) {
  return dusmart(new Showdown.converter().makeHtml(dueval(md)));
}

function dueval(str) {
  return duit(str, /#\{[^\}]*\}/g, function(code) {
    try {
      return eval(code.substr(1));
    } catch (err) {
      return "[" + err + "]"
    }
  }, function(text) { return text });
}

function dusmart(str) {
  var ident = function(text) { return text };
  return duit(str, /<code>[^]+?<\/code>/g, ident, function(str) {
    return duit(str, /<!--[^]+?-->/g, ident, function(str) {
      return duit(str, /<[^>]+>/g, ident, function(s) {
        s = s.replace(/(\S)'/g, "$1&rsquo;");
        s = s.replace(/'(\S)/g, "&lsquo;$1");

        s = s.replace(/(\S)"/g, "$1&rdquo;");
        s = s.replace(/"(\S)/g, "&ldquo;$1");

        s = s.replace(/---([^-])/g, "&mdash;$1");
        s = s.replace(/([^-])---/g, "$1&mdash;");
        s = s.replace(/--([^-])/g, "&ndash;$1");
        s = s.replace(/([^-])--/g, "$1&ndash;");

        return s;
      });
    });
  });
}

function duit(str, re, f_match, f_rest) {
  var rest = ('' + str).split(re);
  var match = str.match(re) || [];
  var out = [], i = 0, j = 0;
  while (i < rest.length || j < match.length) {
    if (i < rest.length) out.push(f_rest(rest[i]));
    if (j < match.length) out.push(f_match(match[j]));
    i++; j++;
  }
  return out.join("");
}

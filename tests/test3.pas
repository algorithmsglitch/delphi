{ Test 3: Inheritance (BONUS) }
program Test3;

type
  TAnimal = class
  private
    FName: String;
    FAge: Integer;
  public
    constructor Create(Name: String; Age: Integer);
    function GetName: String;
    function GetAge: Integer;
    procedure Speak; virtual;
    procedure PrintInfo; virtual;
  end;

  TDog = class(TAnimal)
  private
    FBreed: String;
  public
    constructor Create(Name: String; Age: Integer; Breed: String);
    procedure Speak; override;
    procedure PrintInfo; override;
    function GetBreed: String;
    procedure Fetch;
  end;

  TCat = class(TAnimal)
  private
    FIsIndoor: Boolean;
  public
    constructor Create(Name: String; Age: Integer; IsIndoor: Boolean);
    procedure Speak; override;
    procedure PrintInfo; override;
    function IsIndoorCat: Boolean;
  end;

constructor TAnimal.Create(Name: String; Age: Integer);
begin
  FName := Name;
  FAge := Age;
end;

function TAnimal.GetName: String;
begin
  Result := FName;
end;

function TAnimal.GetAge: Integer;
begin
  Result := FAge;
end;

procedure TAnimal.Speak;
begin
  WriteLn(FName, ' makes a sound.');
end;

procedure TAnimal.PrintInfo;
begin
  WriteLn('Animal Name: ', FName);
  WriteLn('Age: ', FAge);
end;

constructor TDog.Create(Name: String; Age: Integer; Breed: String);
begin
  FName := Name;
  FAge := Age;
  FBreed := Breed;
end;

procedure TDog.Speak;
begin
  WriteLn(FName, ' says: Woof! Woof!');
end;

procedure TDog.PrintInfo;
begin
  WriteLn('Dog Name: ', FName);
  WriteLn('Age: ', FAge);
  WriteLn('Breed: ', FBreed);
end;

function TDog.GetBreed: String;
begin
  Result := FBreed;
end;

procedure TDog.Fetch;
begin
  WriteLn(FName, ' fetches the ball!');
end;

constructor TCat.Create(Name: String; Age: Integer; IsIndoor: Boolean);
begin
  FName := Name;
  FAge := Age;
  FIsIndoor := IsIndoor;
end;

procedure TCat.Speak;
begin
  WriteLn(FName, ' says: Meow~');
end;

procedure TCat.PrintInfo;
begin
  WriteLn('Cat Name: ', FName);
  WriteLn('Age: ', FAge);
  if FIsIndoor then
    WriteLn('Type: Indoor cat')
  else
    WriteLn('Type: Outdoor cat');
end;

function TCat.IsIndoorCat: Boolean;
begin
  Result := FIsIndoor;
end;

var
  Dog1: TDog;
  Cat1: TCat;
  Animal1: TAnimal;

begin
  WriteLn('=== Test 3: Inheritance (BONUS) ===');
  WriteLn('');

  Dog1 := TDog.Create('Rex', 3, 'Labrador');
  Cat1 := TCat.Create('Whiskers', 5, TRUE);

  WriteLn('-- Dog Info --');
  Dog1.PrintInfo;
  WriteLn('');
  Dog1.Speak;
  Dog1.Fetch;

  WriteLn('');
  WriteLn('-- Cat Info --');
  Cat1.PrintInfo;
  WriteLn('');
  Cat1.Speak;

  WriteLn('');
  WriteLn('-- Base class methods on Dog --');
  WriteLn('Name via GetName: ', Dog1.GetName);
  WriteLn('Age via GetAge: ', Dog1.GetAge);

  WriteLn('');
  WriteLn('-- Polymorphism demo via TAnimal base --');
  Animal1 := TAnimal.Create('Generic', 1);
  Animal1.Speak;

  WriteLn('');
  WriteLn('Test 3 Complete!');
end.
